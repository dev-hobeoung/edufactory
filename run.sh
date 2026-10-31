#!/usr/bin/env bash
# EDU FACTORY 실행 관리 — 서버·웹 기동/종료, 속성 관리를 한 곳에서.
#
#   ./run.sh                  대화형 메뉴
#   ./run.sh start [server|web|all]   ./run.sh stop [...]   ./run.sh restart [...]
#   ./run.sh status           ./run.sh logs [server|web]
#   ./run.sh config           속성 편집 (대화형)
#   ./run.sh get              속성 목록     ./run.sh set KEY VALUE     ./run.sh unset KEY
#
# 속성은 .edufactory.env (gitignore, 권한 600) 에 KEY=VALUE 로 저장한다.
# 우선순위: .edufactory.env > 셸 환경변수 > 기본값.
# 로그·PID 는 .run/ 에 쌓인다. DB 는 설정된 컨테이너(DB_CONTAINER)만 켠다 — 다른 컨테이너는 건드리지 않는다.

set -u

ROOT="$(cd "$(dirname "$0")" && pwd)"
CONF="$ROOT/.edufactory.env"
RUN="$ROOT/.run"
mkdir -p "$RUN"

if [ -t 1 ]; then
  B=$'\033[1m'; D=$'\033[2m'; R=$'\033[31m'; G=$'\033[32m'; Y=$'\033[33m'; C=$'\033[36m'; N=$'\033[0m'
else
  B=; D=; R=; G=; Y=; C=; N=
fi
info() { printf '%s\n' "${C}▸${N} $*"; }
ok()   { printf '%s\n' "${G}✔${N} $*"; }
warn() { printf '%s\n' "${Y}!${N} $*"; }
err()  { printf '%s\n' "${R}✘${N} $*" >&2; }

# ── 속성 목록 ─────────────────────────────────────────────────────────────
# 이름|기본값|설명|s(비밀값)   기본값의 {KEY} 는 다른 속성 값으로 바뀐다.
catalog() {
  cat <<'EOF'
PORT|18600|서버 포트 (API = http://localhost:PORT/api)|
WEB_PORT|3010|웹(Vite dev) 포트|
VITE_API_BASE_URL|http://localhost:{PORT}/api|웹이 호출할 API 주소|
EDUFACTORY_CORS_ORIGINS|http://localhost:{WEB_PORT},http://127.0.0.1:{WEB_PORT}|서버 CORS 허용 출처|
DB_CONTAINER|edufactory|기동 시 켤 DB 도커 컨테이너 (빈 값 = 건드리지 않음)|
DATABASE_URL|jdbc:postgresql://localhost:6432/edufactory?currentSchema=edufactory,public|JDBC URL|
DATABASE_USERNAME|postgres|DB 사용자|
DATABASE_PASSWORD|edufactory|DB 비밀번호|s
ANTHROPIC_API_KEY||Claude API 키 (없으면 규칙 기반 구조화, 대화 불가)|s
ANTHROPIC_BASE_URL||Claude API 주소 (빈 값 = 기본)|
EDUFACTORY_MODEL|claude-opus-5-5|대화 모델|
EDUFACTORY_STRUCTURE_MODEL|claude-opus-5-5|구조화 모델|
EDUFACTORY_EFFORT|medium|effort (low/medium/high/xhigh/max)|
EDUFACTORY_MONTHLY_BUDGET_USD|100|월 예산 USD (넘으면 새 대화 차단)|
EDUFACTORY_WEB_SEARCH|true|웹 검색 도구 (true/false)|
VOYAGE_API_KEY||Voyage 임베딩 키 (없으면 로컬 해시 임베딩)|s
VOYAGE_MODEL|voyage-3.5|임베딩 모델 (1024차원)|
EDUFACTORY_STORAGE_DIR|./data/uploads|원본 파일 저장 경로 (server/ 기준)|
EDUFACTORY_ADMIN_EMAIL|admin@edufactory.local|첫 기동 관리자 이메일|
EDUFACTORY_ADMIN_PASSWORD|admin1234|첫 기동 관리자 비밀번호|s
EOF
}

keys() { catalog | cut -d'|' -f1; }
field() { catalog | awk -F'|' -v k="$1" -v n="$2" '$1==k {print $n}'; }
known() { keys | grep -qx "$1"; }

# .edufactory.env 를 CFG_<KEY> 변수로 읽는다 (source 하지 않음 — 값은 그대로 문자열).
load_conf() {
  local k
  for k in $(keys); do unset "CFG_$k"; done
  [ -f "$CONF" ] || return 0
  local line key val
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in ''|\#*) continue ;; esac
    key="${line%%=*}"; val="${line#*=}"
    case "$key" in *[!A-Za-z0-9_]*|'') continue ;; esac
    case "$val" in \"*\") val="${val#\"}"; val="${val%\"}" ;; \'*\') val="${val#\'}"; val="${val%\'}" ;; esac
    printf -v "CFG_$key" '%s' "$val"
  done < "$CONF"
}

# 값의 출처: file | env | default
source_of() {
  local cv="CFG_$1"
  if [ -n "${!cv+x}" ]; then echo file
  elif [ -n "${!1+x}" ]; then echo env
  else echo default; fi
}

value_of() {
  local k="$1" cv="CFG_$1"
  case "$(source_of "$k")" in
    file) printf '%s' "${!cv}" ;;
    env)  printf '%s' "${!k}" ;;
    *)
      local d; d="$(field "$k" 2)"
      while [[ "$d" =~ \{([A-Z_]+)\} ]]; do
        local ref="${BASH_REMATCH[1]}"
        d="${d//\{$ref\}/$(value_of "$ref")}"
      done
      printf '%s' "$d" ;;
  esac
}

mask() {
  local v="$1"
  if [ -z "$v" ]; then printf '%s' "${D}(없음)${N}"
  elif [ ${#v} -le 12 ]; then printf '%s' "••••"
  else printf '%s' "${v:0:4}…${v: -4}"; fi
}

display_value() {
  local v; v="$(value_of "$1")"
  if [ "$(field "$1" 4)" = s ]; then mask "$v"
  elif [ -z "$v" ]; then printf '%s' "${D}(없음)${N}"
  else printf '%s' "$v"; fi
}

save_conf() {  # save_conf KEY VALUE | save_conf KEY  (값 없이 = 삭제)
  local key="$1" tmp="$CONF.tmp" k cv
  load_conf
  if [ $# -ge 2 ]; then printf -v "CFG_$key" '%s' "$2"; else unset "CFG_$key"; fi
  {
    echo "# EDU FACTORY 실행 속성 — ./run.sh config 로 관리 (gitignore 대상, 비밀값 포함)"
    for k in $(keys); do
      cv="CFG_$k"
      [ -n "${!cv+x}" ] && printf '%s=%s\n' "$k" "${!cv}"
    done
  } > "$tmp"
  chmod 600 "$tmp"; mv "$tmp" "$CONF"
  load_conf
}

# 기동할 프로세스에 넘길 환경 (서브셸 안에서 호출)
export_all() {
  local k
  for k in $(keys); do export "$k=$(value_of "$k")"; done
  # 빈 값은 Spring 기본값을 쓰도록 넘기지 않는다
  for k in $(keys); do [ -z "${!k}" ] && unset "$k"; done
  return 0
}

# ── 프로세스 ──────────────────────────────────────────────────────────────
listen_pid() { lsof -nP -t -iTCP:"$1" -sTCP:LISTEN 2>/dev/null | head -1; }
alive() { [ -n "${1:-}" ] && kill -0 "$1" 2>/dev/null; }
read_pid() { [ -f "$RUN/$1.pid" ] && cat "$RUN/$1.pid"; }

descendants() {
  local c
  for c in $(pgrep -P "$1" 2>/dev/null); do descendants "$c"; echo "$c"; done
}

kill_tree() {
  local pids="" p i
  for p in "$@"; do alive "$p" && pids="$pids $(descendants "$p") $p"; done
  [ -z "${pids// /}" ] && return 0
  kill $pids 2>/dev/null
  for i in $(seq 1 30); do
    local left=""
    for p in $pids; do alive "$p" && left="$left $p"; done
    [ -z "$left" ] && return 0
    sleep 0.5
  done
  warn "응답 없음 — 강제 종료:$left"
  kill -9 $left 2>/dev/null
}

server_port() { value_of PORT; }
web_port() { value_of WEB_PORT; }

start_db() {
  local c; c="$(value_of DB_CONTAINER)"
  [ -z "$c" ] && return 0
  command -v docker >/dev/null || { warn "docker 없음 — DB 컨테이너 확인 생략"; return 0; }
  local st; st="$(docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null)"
  case "$st" in
    true) ok "DB 컨테이너 $c 실행 중" ;;
    false) info "DB 컨테이너 $c 시작"; docker start "$c" >/dev/null && ok "DB 컨테이너 $c 시작됨" || err "DB 컨테이너 시작 실패" ;;
    *) warn "DB 컨테이너 '$c' 가 없다 — DATABASE_URL 의 DB 가 떠 있어야 한다" ;;
  esac
}

start_server() {
  local port pid lp; port="$(server_port)"; pid="$(read_pid server)"
  if alive "$pid"; then ok "서버 이미 실행 중 (pid $pid, :$port)"; return 0; fi
  lp="$(listen_pid "$port")"
  if [ -n "$lp" ]; then err "포트 $port 를 다른 프로세스(pid $lp)가 쓰고 있다 — PORT 를 바꾸거나 그 프로세스를 끈다"; return 1; fi
  start_db
  info "서버 기동 (:$port) — 로그 $RUN/server.log"
  ( export_all; cd "$ROOT/server" && exec nohup ./gradlew bootRun --console=plain ) > "$RUN/server.log" 2>&1 < /dev/null &
  pid=$!; echo "$pid" > "$RUN/server.pid"
  local i url="http://localhost:$port/api/health"
  for i in $(seq 1 240); do
    if ! alive "$pid"; then
      err "서버가 기동 중 종료됐다 — 로그 끝부분:"; tail -n 30 "$RUN/server.log" >&2; rm -f "$RUN/server.pid"; return 1
    fi
    if curl -fsS -m 2 "$url" >/dev/null 2>&1; then ok "서버 준비 완료 → http://localhost:$port/api (${i}초)"; return 0; fi
    [ $((i % 15)) -eq 0 ] && info "기다리는 중… ${i}초"
    sleep 1
  done
  warn "240초 안에 /health 응답이 없다 — ./run.sh logs server 로 확인"
}

start_web() {
  local port pid lp; port="$(web_port)"; pid="$(read_pid web)"
  if alive "$pid"; then ok "웹 이미 실행 중 (pid $pid, :$port)"; return 0; fi
  lp="$(listen_pid "$port")"
  if [ -n "$lp" ]; then err "포트 $port 를 다른 프로세스(pid $lp)가 쓰고 있다 — WEB_PORT 를 바꾸거나 그 프로세스를 끈다"; return 1; fi
  if [ ! -d "$ROOT/web/node_modules" ]; then info "npm install"; ( cd "$ROOT/web" && npm install ) || return 1; fi
  info "웹 기동 (:$port) — 로그 $RUN/web.log"
  ( export_all; cd "$ROOT/web" && exec nohup npm run dev -- --port "$port" --strictPort ) > "$RUN/web.log" 2>&1 < /dev/null &
  pid=$!; echo "$pid" > "$RUN/web.pid"
  local i
  for i in $(seq 1 60); do
    if ! alive "$pid"; then err "웹이 기동 중 종료됐다 — 로그 끝부분:"; tail -n 20 "$RUN/web.log" >&2; rm -f "$RUN/web.pid"; return 1; fi
    if [ -n "$(listen_pid "$port")" ]; then ok "웹 준비 완료 → http://localhost:$port"; return 0; fi
    sleep 1
  done
  warn "60초 안에 포트가 열리지 않았다 — ./run.sh logs web 로 확인"
}

stop_one() {  # stop_one server|web
  local name="$1" port pid lp label
  [ "$name" = server ] && { port="$(server_port)"; label=서버; } || { port="$(web_port)"; label=웹; }
  pid="$(read_pid "$name")"
  lp="$(listen_pid "$port")"
  if ! alive "$pid" && [ -z "$lp" ]; then info "$label 꺼져 있음"; rm -f "$RUN/$name.pid"; return 0; fi
  # pid 파일이 없는데 포트만 잡혀 있으면 우리가 띄운 것인지 확인
  if ! alive "$pid" && [ -n "$lp" ]; then
    local cmd; cmd="$(ps -o command= -p "$lp" 2>/dev/null)"
    case "$cmd" in
      *"$ROOT"*|*edufactory*) ;;
      *) warn "포트 $port 는 다른 프로세스(pid $lp: ${cmd:0:80})가 쓰고 있다 — 끄지 않음"; return 1 ;;
    esac
  fi
  info "$label 종료"
  kill_tree $pid $lp
  rm -f "$RUN/$name.pid"
  ok "$label 종료됨"
}

targets() {  # 인자 → server web 목록
  case "${1:-all}" in
    server|api|s) echo server ;;
    web|w) echo web ;;
    all|a|'') echo server web ;;
    *) err "대상은 server | web | all"; return 1 ;;
  esac
}

do_start() { local t; for t in $(targets "${1:-all}") ; do "start_$t" || return 1; done; }
do_stop()  { local t; for t in $(targets "${1:-all}") ; do stop_one "$t"; done; }
do_restart() { do_stop "${1:-all}"; do_start "${1:-all}"; }

do_status() {
  local p pid lp
  printf '%s\n' "${B}상태${N}"
  p="$(server_port)"; pid="$(read_pid server)"; lp="$(listen_pid "$p")"
  if curl -fsS -m 2 "http://localhost:$p/api/health" >/dev/null 2>&1; then
    printf '  서버  %s  http://localhost:%s/api  %s\n' "${G}● 실행${N}" "$p" "${D}pid ${pid:-?}${N}"
  elif alive "$pid"; then
    printf '  서버  %s  :%s  %s\n' "${Y}● 기동 중/응답 없음${N}" "$p" "${D}pid $pid${N}"
  elif [ -n "$lp" ]; then
    printf '  서버  %s  :%s 를 pid %s 가 사용 중\n' "${Y}● 알 수 없음${N}" "$p" "$lp"
  else
    printf '  서버  %s\n' "${D}○ 꺼짐${N}"
  fi
  p="$(web_port)"; pid="$(read_pid web)"; lp="$(listen_pid "$p")"
  if [ -n "$lp" ]; then
    printf '  웹    %s  http://localhost:%s  %s\n' "${G}● 실행${N}" "$p" "${D}pid ${pid:-$lp}${N}"
  elif alive "$pid"; then
    printf '  웹    %s  %s\n' "${Y}● 기동 중${N}" "${D}pid $pid${N}"
  else
    printf '  웹    %s\n' "${D}○ 꺼짐${N}"
  fi
  local c; c="$(value_of DB_CONTAINER)"
  if [ -n "$c" ] && command -v docker >/dev/null; then
    case "$(docker inspect -f '{{.State.Running}}' "$c" 2>/dev/null)" in
      true) printf '  DB    %s  컨테이너 %s\n' "${G}● 실행${N}" "$c" ;;
      false) printf '  DB    %s  컨테이너 %s\n' "${D}○ 꺼짐${N}" "$c" ;;
      *) printf '  DB    %s  컨테이너 %s 없음\n' "${Y}?${N}" "$c" ;;
    esac
  fi
}

do_logs() {
  local name; name="$(targets "${1:-server}")" || return 1
  [ "$name" = "server web" ] && name=server
  [ -f "$RUN/$name.log" ] || { warn "$RUN/$name.log 없음"; return 0; }
  info "$name 로그 (Ctrl-C 로 나감)"
  trap ':' INT
  tail -n 80 -f "$RUN/$name.log"
  trap - INT
  echo
}

# ── 속성 ──────────────────────────────────────────────────────────────────
src_tag() {
  case "$(source_of "$1")" in
    file) printf '%s' "${G}파일${N}" ;;
    env)  printf '%s' "${Y}환경${N}" ;;
    *)    printf '%s' "${D}기본${N}" ;;
  esac
}

print_props() {
  local i=0 k
  for k in $(keys); do
    i=$((i + 1))
    printf '  %s%2d)%s %-30s %s  %s\n' "$B" "$i" "$N" "$k" "$(src_tag "$k")" "$(display_value "$k")"
  done
}

edit_prop() {
  local k="$1" desc def secret cur new
  desc="$(field "$k" 3)"; def="$(field "$k" 2)"; secret="$(field "$k" 4)"
  echo
  printf '%s\n' "${B}$k${N} — $desc"
  printf '  현재: %s (%s)\n' "$(display_value "$k")" "$(src_tag "$k")"
  [ -n "$def" ] && printf '  기본: %s\n' "$def"
  printf '  %s\n' "${D}Enter = 그대로, '-' = 파일에서 지움(환경/기본값 사용), '\"\"' = 빈 값으로 고정${N}"
  if [ "$secret" = s ]; then
    read -r -s -p "  새 값 (입력 숨김): " new; echo
  else
    read -r -e -p "  새 값: " new
  fi
  case "$new" in
    '') info "변경 없음" ;;
    -) save_conf "$k"; ok "$k → $(display_value "$k") ($(src_tag "$k"))" ;;
    '""') save_conf "$k" ""; ok "$k → 빈 값" ;;
    *) save_conf "$k" "$new"; ok "$k → $(display_value "$k")" ;;
  esac
  [ -z "$new" ] && return 0
  case "$k" in
    DB_CONTAINER) ;;
    VITE_API_BASE_URL) warn "실행 중인 웹은 재시작해야 반영된다" ;;
    WEB_PORT) warn "실행 중인 웹은 재시작해야 반영된다 (서버 CORS 기본값도 바뀌므로 서버도 재시작)" ;;
    PORT) warn "실행 중인 서버는 재시작해야 반영된다 (웹 API 주소 기본값도 바뀌므로 웹도 재시작)" ;;
    *) warn "실행 중인 서버는 재시작해야 반영된다" ;;
  esac
}

config_menu() {
  local n total k
  total="$(keys | wc -l | tr -d ' ')"
  while :; do
    echo
    printf '%s\n' "${B}속성${N} ${D}($CONF — 출처: 파일 > 환경 > 기본)${N}"
    print_props
    echo
    read -r -p "편집할 번호 (Enter = 돌아가기): " n
    [ -z "$n" ] && return 0
    if [[ "$n" =~ ^[0-9]+$ ]] && [ "$n" -ge 1 ] && [ "$n" -le "$total" ]; then
      k="$(keys | sed -n "${n}p")"
      edit_prop "$k"
    elif known "$n"; then
      edit_prop "$n"
    else
      warn "1~$total 사이 번호나 속성 이름을 넣는다"
    fi
  done
}

# ── 메뉴 ──────────────────────────────────────────────────────────────────
pick_target() {  # 메뉴에서 대상 고르기
  local t
  read -r -p "  대상 [a]전체 / [s]서버 / [w]웹 (Enter = 전체): " t
  targets "${t:-all}"
}

menu() {
  local c t
  while :; do
    echo
    printf '%s\n' "${B}━━ EDU FACTORY ━━${N}"
    do_status
    echo
    echo "  1) 기동      2) 종료      3) 재시작"
    echo "  4) 속성      5) 로그      6) 상태"
    echo "  q) 나가기  ${D}(서버·웹은 백그라운드로 계속 돈다)${N}"
    read -r -p "> " c || { echo; return 0; }
    case "$c" in
      1) t="$(pick_target)" && for x in $t; do "start_$x" || break; done ;;
      2) t="$(pick_target)" && for x in $t; do stop_one "$x"; done ;;
      3) t="$(pick_target)" && { for x in $t; do stop_one "$x"; done; for x in $t; do "start_$x" || break; done; } ;;
      4) config_menu ;;
      5) read -r -p "  [s]서버 / [w]웹 (Enter = 서버): " t; do_logs "${t:-server}" ;;
      6) ;;
      q|Q|exit|quit) return 0 ;;
      '') ;;
      *) warn "알 수 없는 선택: $c" ;;
    esac
  done
}

usage() { sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; }

main() {
  load_conf
  local cmd="${1:-menu}"; [ $# -gt 0 ] && shift
  case "$cmd" in
    menu) menu ;;
    start|up) do_start "${1:-all}" ;;
    stop|down) do_stop "${1:-all}" ;;
    restart) do_restart "${1:-all}" ;;
    status|st) do_status ;;
    logs|log) do_logs "${1:-server}" ;;
    config|cfg) config_menu ;;
    get) if [ $# -gt 0 ]; then value_of "$1"; echo; else print_props; fi ;;
    set)
      [ $# -ge 2 ] || { err "사용법: ./run.sh set KEY VALUE"; exit 1; }
      known "$1" || { err "목록에 없는 속성: $1 (./run.sh get 으로 목록 확인)"; exit 1; }
      save_conf "$1" "$2"; ok "$1 저장" ;;
    unset)
      [ $# -ge 1 ] || { err "사용법: ./run.sh unset KEY"; exit 1; }
      save_conf "$1"; ok "$1 지움 → $(display_value "$1") ($(src_tag "$1"))" ;;
    -h|--help|help) usage ;;
    *) err "알 수 없는 명령: $cmd"; usage; exit 1 ;;
  esac
}

main "$@"
