#!/usr/bin/env bash
# EDU FACTORY 실행 관리 — 서버·웹 기동/종료, 속성 관리를 한 곳에서.
#
#   ./run.sh                  키보드 메뉴 (↑↓ 이동 · Space 선택/값 바꾸기 · Enter 실행 · Esc/q 뒤로)
#   ./run.sh start [server|web|all]   ./run.sh stop [...]   ./run.sh restart [...]
#   ./run.sh status           ./run.sh logs [server|web]
#   ./run.sh config           속성 편집 (키보드 메뉴)
#   ./run.sh get              속성 목록     ./run.sh set KEY VALUE     ./run.sh unset KEY
#
# 속성은 .edufactory.env (gitignore, 권한 600) 에 KEY=VALUE 로 저장한다.
# 우선순위: .edufactory.env > 셸 환경변수 > 기본값.
# 로그·PID 는 .run/ 에 쌓인다. DB 는 설정된 컨테이너(DB_CONTAINER)만 켠다 — 다른 컨테이너는 건드리지 않는다.

# `. ./run.sh` / `source run.sh` 로 불러도 현재 셸(zsh 등)이 아니라 새 bash 프로세스에서 돌린다.
# (zsh 는 문법이 달라 깨지고, 스크립트의 exit 가 터미널 셸을 닫아 버린다)
if [ -n "${ZSH_VERSION:-}" ]; then
  bash "$0" "$@"; return $?
elif [ -n "${BASH_VERSION:-}" ] && [ "${BASH_SOURCE[0]}" != "$0" ]; then
  bash "${BASH_SOURCE[0]}" "$@"; return $?
fi

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
# 이름|기본값|설명|s(비밀값)|선택지(공백 구분)   기본값의 {KEY} 는 다른 속성 값으로 바뀐다.
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
EDUFACTORY_MODEL|claude-opus-5-5|대화 모델||claude-opus-5-5 claude-sonnet-5-5 claude-haiku-4-5-20251001 claude-fable-5-1
EDUFACTORY_STRUCTURE_MODEL|claude-opus-5-5|구조화 모델||claude-opus-5-5 claude-sonnet-5-5 claude-haiku-4-5-20251001 claude-fable-5-1
EDUFACTORY_EFFORT|medium|effort||low medium high xhigh max
EDUFACTORY_MONTHLY_BUDGET_USD|100|월 예산 USD (넘으면 새 대화 차단)|
EDUFACTORY_WEB_SEARCH|true|웹 검색 도구||true false
VOYAGE_API_KEY||Voyage 임베딩 키 (없으면 로컬 해시 임베딩)|s
VOYAGE_MODEL|voyage-3.5|임베딩 모델 (1024차원)|
EDUFACTORY_STORAGE_DIR|./data/uploads|원본 파일 저장 경로 (server/ 기준)|
EDUFACTORY_ADMIN_EMAIL|admin@edufactory.local|첫 기동 관리자 이메일|
EDUFACTORY_ADMIN_PASSWORD|admin1234|첫 기동 관리자 비밀번호|s
EOF
}

CAT_KEYS=(); CAT_DEF=(); CAT_DESC=(); CAT_SEC=(); CAT_CHO=()
while IFS='|' read -r _k _d _ds _s _c; do
  CAT_KEYS+=("$_k"); CAT_DEF+=("$_d"); CAT_DESC+=("$_ds"); CAT_SEC+=("$_s"); CAT_CHO+=("$_c")
done < <(catalog)

cat_idx() {
  local i=0 k
  for k in "${CAT_KEYS[@]}"; do [ "$k" = "$1" ] && { echo $i; return 0; }; i=$((i + 1)); done
  return 1
}
keys() { printf '%s\n' "${CAT_KEYS[@]}"; }
known() { cat_idx "$1" >/dev/null; }
field() {  # field KEY 2(기본값)|3(설명)|4(비밀)|5(선택지)
  local i; i="$(cat_idx "$1")" || return 1
  case "$2" in
    2) printf '%s' "${CAT_DEF[$i]}" ;; 3) printf '%s' "${CAT_DESC[$i]}" ;;
    4) printf '%s' "${CAT_SEC[$i]}" ;; 5) printf '%s' "${CAT_CHO[$i]}" ;;
  esac
}

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

# set_prop KEY VALUE | set_prop KEY (파일에서 지움 → 환경/기본값). 결과 문구는 MSG 에 남긴다.
MSG=
set_prop() {
  if [ $# -ge 2 ]; then save_conf "$1" "$2"; else save_conf "$1"; fi
  MSG="${G}✔${N} $1 → $(display_value "$1") ($(src_tag "$1"))"
  case "$1" in
    DB_CONTAINER) ;;
    VITE_API_BASE_URL) MSG="$MSG  ${Y}웹 재시작 후 반영${N}" ;;
    PORT|WEB_PORT) MSG="$MSG  ${Y}서버·웹 재시작 후 반영${N}" ;;
    *) MSG="$MSG  ${Y}서버 재시작 후 반영${N}" ;;
  esac
}

# ── 키보드 메뉴 (↑↓ 이동 · Space 선택 · Enter 실행) ──────────────────────
cursor_off() { printf '\033[?25l'; }
cursor_on() { printf '\033[?25h'; }

read_key() {  # KEY = up | down | enter | space | esc | quit | del | eof | 그 밖의 글자
  local k rest=
  IFS= read -rsn1 k || { KEY=eof; return 0; }
  case "$k" in
    $'\e')
      IFS= read -rsn2 -t 1 rest 2>/dev/null
      case "$rest" in
        '[A'|OA) KEY=up ;;
        '[B'|OB) KEY=down ;;
        '[3') IFS= read -rsn1 -t 1 rest 2>/dev/null; KEY=del ;;
        '') KEY=esc ;;
        *) KEY=other ;;
      esac ;;
    '') KEY=enter ;;
    ' ') KEY=space ;;
    k|K) KEY=up ;;
    j|J) KEY=down ;;
    q|Q) KEY=quit ;;
    *) KEY="$k" ;;
  esac
}

# choose 머리말함수 설명함수 안내문 시작위치 항목... → SEL(고른 위치), KEY(누른 키)
# 화면보다 항목이 많으면 스크롤한다.
choose() {
  local head="$1" detail="$2" hint="$3" n i item h dt frame rows avail top=0 nl t
  SEL="$4"; shift 4; n=$#
  while :; do
    h="$("$head")"; dt=
    [ -n "$detail" ] && dt="$("$detail" "$SEL")"
    rows="$(tput lines 2>/dev/null || echo 24)"
    t="${h//[!$'\n']/}"; nl=$(( ${#t} + 1 ))
    if [ -n "$dt" ]; then t="${dt//[!$'\n']/}"; nl=$(( nl + ${#t} + 1 )); fi
    [ -n "$MSG" ] && nl=$((nl + 2))
    avail=$(( rows - nl - 5 )); [ $avail -lt 3 ] && avail=3
    [ "$SEL" -lt $top ] && top=$SEL
    [ "$SEL" -ge $((top + avail)) ] && top=$((SEL - avail + 1))
    [ $n -le $avail ] && top=0

    frame="$h"$'\n'
    if [ $top -gt 0 ]; then frame+="    ${D}↑ ${top}개 더${N}"$'\n'; else frame+=$'\n'; fi
    i=0
    for item in "$@"; do
      if [ $i -ge $top ] && [ $i -lt $((top + avail)) ]; then
        if [ $i -eq "$SEL" ]; then frame+="  ${C}${B}❯${N} ${B}${item}${N}"$'\n'
        else frame+="    ${item}"$'\n'; fi
      fi
      i=$((i + 1))
    done
    if [ $((top + avail)) -lt $n ]; then frame+="    ${D}↓ $((n - top - avail))개 더${N}"$'\n'; else frame+=$'\n'; fi
    [ -n "$dt" ] && frame+="$dt"$'\n'
    frame+=$'\n'"  ${D}${hint}${N}"
    [ -n "$MSG" ] && frame+=$'\n\n'"  $MSG"
    frame="${frame//$'\n'/$'\033[K\n'}"
    printf '\033[H%s\033[K\033[J' "$frame"
    MSG=

    read_key
    case "$KEY" in
      up) SEL=$(( (SEL + n - 1) % n )) ;;
      down) SEL=$(( (SEL + 1) % n )) ;;
      other) ;;
      *) return 0 ;;
    esac
  done
}

pause() {
  printf '\n  %s' "${D}아무 키나 누르면 돌아간다${N}"
  cursor_off; read_key
}

STATUS_TEXT=
refresh_status() { STATUS_TEXT="$(do_status)"; }
head_main() { printf '%s\n\n%s\n' "${B}━━ EDU FACTORY ━━${N}" "$STATUS_TEXT"; }

ACTION=
head_targets() { head_main; printf '\n  %s\n' "${B}${ACTION} 대상${N}"; }

# pick_targets 기동|종료|재시작 → TARGETS, 취소하면 1
pick_targets() {
  ACTION="$1"
  local s=1 w=1 sel=2 bs bw
  while :; do
    [ $s = 1 ] && bs="${G}[x]${N}" || bs="[ ]"
    [ $w = 1 ] && bw="${G}[x]${N}" || bw="[ ]"
    choose head_targets "" "↑↓ 이동 · Space 선택/해제 · Enter 실행 · Esc/q 취소" $sel \
      "$bs 서버" "$bw 웹" "▶ $ACTION"
    sel=$SEL
    case "$KEY" in
      space) case $sel in 0) s=$((1 - s)) ;; 1) w=$((1 - w)) ;; 2) KEY=enter ;; esac ;;
    esac
    case "$KEY" in
      enter)
        TARGETS=; [ $s = 1 ] && TARGETS=server; [ $w = 1 ] && TARGETS="$TARGETS web"
        [ -n "${TARGETS// /}" ] && return 0
        MSG="${Y}!${N} 하나 이상 고른다" ;;
      esc|quit|eof) return 1 ;;
    esac
  done
}

run_action() {
  local t
  printf '\033[H\033[2J'; cursor_on
  printf '%s\n\n' "${B}━━ $1 ━━${N}"
  case "$1" in
    기동) for t in $TARGETS; do "start_$t" || break; done ;;
    종료) for t in $TARGETS; do stop_one "$t"; done ;;
    재시작) for t in $TARGETS; do stop_one "$t"; done; for t in $TARGETS; do "start_$t" || break; done ;;
  esac
  pause
}

tui_logs() {
  if [ ! -f "$RUN/$1.log" ]; then MSG="${Y}!${N} $1 로그가 아직 없다 ($RUN/$1.log)"; return 0; fi
  printf '\033[H\033[2J'; cursor_on
  do_logs "$1"
  trap 'exit 130' INT
  cursor_off
}

PROP_ITEMS=()
build_prop_items() {
  local k v w
  w=$(( $(tput cols 2>/dev/null || echo 100) - 44 )); [ $w -lt 20 ] && w=20
  PROP_ITEMS=()
  for k in "${CAT_KEYS[@]}"; do
    v="$(display_value "$k")"
    [ ${#v} -gt $w ] && v="${v:0:$((w - 1))}…"
    PROP_ITEMS+=("$(printf '%-30s %s  %s' "$k" "$(src_tag "$k")" "$v")")
  done
}

head_props() { printf '%s  %s\n' "${B}━━ 속성 ━━${N}" "${D}$CONF · 출처 파일 > 환경 > 기본${N}"; }

detail_prop() {
  local i="$1"
  printf '  %s\n' "${CAT_DESC[$i]}"
  [ -n "${CAT_DEF[$i]}" ] && [ "${CAT_SEC[$i]}" != s ] && printf '  %s\n' "${D}기본값 ${CAT_DEF[$i]}${N}"
  [ -n "${CAT_CHO[$i]}" ] && printf '  %s\n' "${D}Space 로 차례로 바꾸기: ${CAT_CHO[$i]}${N}"
  return 0
}

EDIT_KEY=
head_edit() {
  local i; i="$(cat_idx "$EDIT_KEY")"
  printf '%s\n\n' "${B}━━ $EDIT_KEY ━━${N}"
  printf '  %s\n' "${CAT_DESC[$i]}"
  printf '  현재  %s (%s)\n' "$(display_value "$EDIT_KEY")" "$(src_tag "$EDIT_KEY")"
  [ -n "${CAT_DEF[$i]}" ] && [ "${CAT_SEC[$i]}" != s ] && printf '  기본  %s\n' "${CAT_DEF[$i]}"
  return 0
}

cycle_prop() {  # 선택지에서 다음 값으로
  local k="$1" cur c first= next= found=0
  cur="$(value_of "$k")"
  for c in $2; do
    [ -z "$first" ] && first="$c"
    if [ $found = 1 ]; then next="$c"; break; fi
    [ "$c" = "$cur" ] && found=1
  done
  set_prop "$k" "${next:-$first}"
}

edit_prop() {
  local k="$1" i new
  i="$(cat_idx "$k")"; EDIT_KEY="$k"
  if [ -n "${CAT_CHO[$i]}" ]; then
    local opts=() c cur start=0 j=0
    cur="$(value_of "$k")"
    for c in ${CAT_CHO[$i]}; do opts+=("$c"); [ "$c" = "$cur" ] && start=$j; j=$((j + 1)); done
    opts+=("${D}직접 입력…${N}" "${D}기본값으로 (파일에서 지움)${N}")
    choose head_edit "" "↑↓ 이동 · Enter 고르기 · Esc/q 취소" $start "${opts[@]}"
    case "$KEY" in enter|space) ;; *) return 0 ;; esac
    if [ "$SEL" -lt $j ]; then set_prop "$k" "${opts[$SEL]}"; return 0; fi
    if [ "$SEL" -eq $((j + 1)) ]; then set_prop "$k"; return 0; fi
  fi
  printf '\033[H\033[2J'; head_edit; echo
  printf '  %s\n\n' "${D}Enter = 그대로 · '-' = 기본값으로(파일에서 지움) · '\"\"' = 빈 값${N}"
  cursor_on
  if [ "${CAT_SEC[$i]}" = s ]; then read -r -s -p "  새 값 (입력 숨김): " new; echo
  else read -r -e -p "  새 값: " new; fi
  cursor_off
  case "$new" in
    '') MSG="${D}$k 변경 없음${N}" ;;
    -) set_prop "$k" ;;
    '""') set_prop "$k" "" ;;
    *) set_prop "$k" "$new" ;;
  esac
}

tui_props() {
  local sel=0 k
  build_prop_items
  while :; do
    choose head_props detail_prop "↑↓ 이동 · Enter 편집 · Space 다음 값(선택형) · d 기본값으로 · Esc/q 돌아가기" \
      $sel "${PROP_ITEMS[@]}"
    sel=$SEL; k="${CAT_KEYS[$sel]}"
    case "$KEY" in
      quit|esc|eof) return 0 ;;
      enter) edit_prop "$k" ;;
      space) if [ -n "${CAT_CHO[$sel]}" ]; then cycle_prop "$k" "${CAT_CHO[$sel]}"; else edit_prop "$k"; fi ;;
      d|D|del) set_prop "$k" ;;
      *) continue ;;
    esac
    build_prop_items
  done
}

tui_main() {
  local sel=0
  local items=(
    "기동        ${D}서버·웹 켜기 (DB 컨테이너가 꺼져 있으면 함께)${N}"
    "종료        ${D}서버·웹 끄기${N}"
    "재시작      ${D}끄고 다시 켜기${N}"
    "속성        ${D}포트 · DB · API 키 · 모델 …${N}"
    "서버 로그   ${D}Ctrl-C 로 돌아옴${N}"
    "웹 로그     ${D}Ctrl-C 로 돌아옴${N}"
    "나가기      ${D}서버·웹은 계속 돈다${N}"
  )
  refresh_status
  while :; do
    choose head_main "" "↑↓ 이동 · Enter 선택 · r 상태 새로고침 · Esc/q 나가기" $sel "${items[@]}"
    sel=$SEL
    case "$KEY" in
      quit|esc|eof) return 0 ;;
      r|R) refresh_status; continue ;;
      enter|space) ;;
      *) continue ;;
    esac
    case $sel in
      0) pick_targets 기동 && run_action 기동 ;;
      1) pick_targets 종료 && run_action 종료 ;;
      2) pick_targets 재시작 && run_action 재시작 ;;
      3) tui_props ;;
      4) tui_logs server ;;
      5) tui_logs web ;;
      6) return 0 ;;
    esac
    refresh_status
  done
}

# 대체 화면에서 키보드 메뉴를 돌리고, 끝나면 원래 화면으로 돌아와 상태를 남긴다.
tui() {
  interactive || return 1
  trap 'cursor_on; stty echo 2>/dev/null; printf "\033[?1049l"' EXIT
  trap 'exit 130' INT
  printf '\033[?1049h\033[H\033[2J'; cursor_off
  "$@"
  cursor_on; printf '\033[?1049l'; trap - EXIT
  do_status
}

# 표준입력이 터미널이 아니면(IDE 실행 버튼, 파이프 등) 메뉴가 입력을 못 받고 바로 끝나므로 터미널에 다시 붙인다.
interactive() {
  [ -t 0 ] && return 0
  if { exec 3</dev/tty; } 2>/dev/null; then exec 0<&3 3<&-; return 0; fi
  do_status; echo
  err "입력을 받을 터미널이 없어 대화형 메뉴를 띄울 수 없다 — 터미널 창에서 ./run.sh 를 실행하거나, ./run.sh start 처럼 명령을 붙인다"
  echo; usage; return 1
}

usage() { sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'; }

main() {
  load_conf
  local cmd="${1:-menu}"; [ $# -gt 0 ] && shift
  case "$cmd" in
    menu) tui tui_main ;;
    start|up) do_start "${1:-all}" ;;
    stop|down) do_stop "${1:-all}" ;;
    restart) do_restart "${1:-all}" ;;
    status|st) do_status ;;
    logs|log) do_logs "${1:-server}" ;;
    config|cfg) tui tui_props ;;
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
