# EDU FACTORY

자료를 구조화·임베딩해 라이브러리로 쌓고, 라벨·시험 성적·조건별 컨텍스트 규칙을 근거로 **범용 문제·교육자료**
(시험지, 퀴즈, 학습지, 개념 정리, 요약 노트, 플래시카드, 해설 자료)를 Claude 와 대화하며 만드는 작업실.

| 경로 | 설명 |
| --- | --- |
| `server/` | Spring Boot 3.4 / Java 21 (Gradle). Anthropic Java SDK, Voyage 임베딩, PostgreSQL + pgvector, Flyway |
| `web/` | React 18 + TypeScript + Vite + Tailwind + react-query + KaTeX |
| `docs/API.md` | API 계약 (서버·웹 공통 기준) |

## 기능

1. **자료 라이브러리** — PDF·DOCX·TXT·MD 업로드나 붙여넣기 → 텍스트 추출 → Claude 구조화(문항·지문·개념·예제·해설·단락 청크,
   선택지·정답·배점·쪽 번호, 떨어진 정답표 자동 연결, 스캔 PDF 는 PDF 그대로 전달) → Voyage 임베딩(pgvector, HNSW).
   청크는 화면에서 고칠 수 있고 고치면 다시 임베딩된다.
2. **라벨** — `그룹 / 이름`(과목/수학, 학년/고2, 단원/미분 …). 자료·청크 단위로 붙이고(구조화 때 AI 가 기존 라벨 중 맞는 것을 제안),
   임베딩 입력 앞에 라벨 문구가 들어간다. 검색 필터는 같은 그룹 OR, 그룹끼리 AND.
3. **시험·성적** — 시험 자료의 문항에 배점·정답을 두고 성적 세트(회차·집단)별로 점수나 학생 응답을 입력(엑셀 붙여넣기·CSV).
   응답은 정답과 비교해 자동 채점(①↔1, 복수 정답). 문항 정답률·만점률·0점 비율·변별도(상위27%−하위27%)·선택지 분포,
   시험 총점 분포(분위수·히스토그램). 정답을 고치면 다시 채점된다.
4. **컨텍스트 규칙** — 조건(라벨·산출물 종류·문항 유형·자료·대상·요청 키워드, ALL/ANY)이 맞으면 지침·고정 자료 본문·라벨 범위
   검색 결과를 요청에 주입한다. 화면에서 미리보기로 실제 주입 블록을 확인할 수 있다.
5. **작업실** — 조건(산출물 종류·참고 라벨·필수 자료·문항 유형·수·총점·목표 정답률·대상·창의성)을 정하고 대화. Claude 가 도구
   (자료 검색, 자료 전체 보기, 정답률로 문항 찾기, 시험 통계, 예상 정답률, 웹 검색)로 근거를 찾아 산출물을 제출한다.
6. **산출물** — 섹션(본문) + 문항(객관식·복수선택·OX·단답·빈칸·서술형·짝짓기·배열·플래시카드) 구조. 제출마다 검증,
   라이브러리 유사도(원문 베끼기 경고), 성적 기반 kNN 예상 정답률을 계산해 버전으로 쌓는다. 직접 작성·편집·되돌리기,
   문제지/정답해설지 인쇄(PDF), Markdown 내보내기.

## 실행

```bash
# DB: docker 컨테이너 edufactory (pgvector/pgvector:pg17) — localhost:6432, DB edufactory, postgres/edufactory
#     테이블은 edufactory 스키마에 Flyway 가 만든다 (vector 확장은 public)

cd server
ANTHROPIC_API_KEY=... VOYAGE_API_KEY=... ./gradlew bootRun     # http://localhost:18600/api
./gradlew test

cd web
npm install
npm run dev                                                   # http://localhost:3010
```

첫 기동 시 관리자 계정 `admin@edufactory.local` / `admin1234` 가 만들어진다 (`EDUFACTORY_ADMIN_EMAIL`·`EDUFACTORY_ADMIN_PASSWORD`).
운영에서는 반드시 바꾼다.

키 없이도 돌아간다:
- `ANTHROPIC_API_KEY` 없음 → 구조화는 규칙 기반 분할(번호·①~⑤ 선택지·제목), 작업실 대화는 AI_ERROR.
- `VOYAGE_API_KEY` 없음 → 로컬 해시 임베딩(`local-hash-v1`, 개발용). 키를 넣고 재기동하면 모든 청크를 Voyage 로 다시 임베딩한다.

| 환경변수 | 기본값 |
| --- | --- |
| `DATABASE_URL` / `DATABASE_USERNAME` / `DATABASE_PASSWORD` | `jdbc:postgresql://localhost:6432/edufactory?currentSchema=edufactory,public` / `postgres` / `edufactory` |
| `EDUFACTORY_MODEL` · `EDUFACTORY_STRUCTURE_MODEL` | `claude-opus-5-5` (대화 모델은 설정 화면에서 바꿀 수 있음) |
| `EDUFACTORY_EFFORT` | `medium` |
| `EDUFACTORY_MONTHLY_BUDGET_USD` | `100` (넘으면 새 대화 요청을 막음) |
| `EDUFACTORY_WEB_SEARCH` | `true` (창의성 단계의 외부 자료 — 조직에서 웹 검색이 켜져 있어야 함) |
| `VOYAGE_MODEL` | `voyage-3.5` (1024차원 고정 — 바꾸면 `ef_chunk.embedding` 재생성) |
| `EDUFACTORY_STORAGE_DIR` | `./data/uploads` (원본 파일) |
| `EDUFACTORY_CORS_ORIGINS` | `http://localhost:3010,http://127.0.0.1:3010` |
