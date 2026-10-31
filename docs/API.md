# EDU FACTORY API 계약

서버(`server/`, Spring Boot)가 제공하고 웹(`web/`)이 쓴다.

- Base URL: `VITE_API_BASE_URL` (기본 `http://localhost:18600/api`). 아래 경로는 `/api` 뒤를 기준으로 적는다.
- 인증: `POST /auth/login` 으로 받은 토큰을 `Authorization: Bearer <token>` 헤더로 보낸다. 없거나 만료면 401.
- 성공 응답은 본문이 곧 데이터(JSON). 실패는 HTTP 4xx/5xx + `{ code: string, message: string }`
  (`code`: `BAD_REQUEST`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `AI_ERROR`, `BUDGET_EXCEEDED`, `INTERNAL`).
- 날짜는 ISO-8601 문자열(UTC). 비율은 0~1 실수(화면에서 %로 표시). ID는 UUID 문자열.
- 본문 텍스트(자료 청크·산출물)는 마크다운 + LaTeX(`$…$` 인라인, `$$…$$` 블록).

## 0. 인증·사용자

```ts
type User = { id: string; email: string; name: string; role: "ADMIN" | "EDITOR" };
```

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| `POST /auth/login` | `{ email, password }` | `{ token: string, user: User }` (토큰 30일) |
| `POST /auth/logout` | — | `204` |
| `GET /auth/me` | — | `User` |
| `GET /users` | — | `User[]` (ADMIN) |
| `POST /users` | `{ email, name, password, role }` | `User` (ADMIN) |
| `DELETE /users/{id}` | — | `204` (ADMIN, 자기 자신 불가) |

첫 기동 시 사용자가 없으면 `edufactory.admin.email` / `edufactory.admin.password`(기본 `admin@edufactory.local` / `admin1234`) ADMIN 을 만든다.

## 1. 라벨

라벨은 `그룹 / 이름` 쌍이다 (예: `과목 / 수학`, `학년 / 고2`, `단원 / 미분`, `유형 / 기출`). 자료와 청크에 붙고,
검색 필터·컨텍스트 규칙 조건·임베딩 텍스트(라벨 문구가 임베딩 입력 앞에 붙는다)에 쓰인다.

```ts
type Label = { id: string; group: string; name: string; color: string | null; description: string | null;
               materialCount: number; chunkCount: number };
type LabelRef = { id: string; group: string; name: string; color: string | null };
```

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| `GET /labels` | — | `Label[]` (group, name 순) |
| `POST /labels` | `{ group, name, color?, description? }` | `Label` (같은 group+name 이면 409) |
| `PATCH /labels/{id}` | `{ group?, name?, color?, description? }` | `Label` (이름이 바뀌면 붙은 청크 재임베딩 예약) |
| `DELETE /labels/{id}` | — | `204` (붙어 있던 자료·청크에서 떨어지고 규칙 조건에서도 빠진다) |

## 2. 자료 (업로드 → 구조화 → 임베딩)

```ts
type MaterialKind = "EXAM" | "WORKSHEET" | "TEXTBOOK" | "NOTE" | "REFERENCE" | "OTHER";
type MaterialStatus = "QUEUED" | "EXTRACTING" | "STRUCTURING" | "EMBEDDING" | "READY" | "FAILED";
type SourceType = "PDF" | "DOCX" | "TEXT" | "MARKDOWN" | "PASTE";
type ChunkType = "QUESTION" | "PASSAGE" | "CONCEPT" | "EXPLANATION" | "EXAMPLE" | "SECTION" | "OTHER";
type QuestionType = "MULTIPLE_CHOICE" | "MULTI_SELECT" | "TRUE_FALSE" | "SHORT_ANSWER" | "FILL_BLANK"
                  | "ESSAY" | "MATCHING" | "ORDERING" | "FLASHCARD";

type MaterialSummary = {
  id: string; title: string; kind: MaterialKind; description: string | null;
  sourceType: SourceType; fileName: string | null; fileSize: number | null;
  isExam: boolean;                     // 시험 자료 — 문항(QUESTION 청크)에 배점·정답을 두고 성적을 입력할 수 있다
  status: MaterialStatus; statusMessage: string | null; error: string | null;
  labels: LabelRef[];
  chunkCount: number; embeddedCount: number;
  scoreSetCount: number;               // 성적 세트 수 (있으면 재구조화 불가)
  structuredBy: "AI" | "RULE" | null;  // AI = Claude 구조화, RULE = 키 없을 때 규칙 기반 분할
  createdAt: string; updatedAt: string;
};
type Choice = { label: string; text: string };   // label 예: "①", "A", "1"
type ChunkLabel = LabelRef & { source: "MANUAL" | "AI" };
type Chunk = {
  id: string; materialId: string; seq: number; chunkType: ChunkType;
  title: string | null; position: string | null;   // 위치 표시 (예: "3번", "2장 1절", "p.4")
  itemNo: string | null;               // 문항 번호 (QUESTION)
  questionType: QuestionType | null;
  content: string;                     // 본문 (마크다운 + LaTeX)
  choices: Choice[]; answer: string | null; explanation: string | null;
  score: number | null;                // 배점 (시험 문항)
  tags: string[];                      // 자유 키워드
  pageFrom: number | null; pageTo: number | null;
  labels: ChunkLabel[];                // 청크 자체 라벨 (자료 라벨은 MaterialSummary.labels — 검색 시 둘 다 적용)
  embedded: boolean;
  stat: ItemStat | null;               // 시험 문항이고 성적이 있으면
};
type MaterialDetail = MaterialSummary & { chunks: Chunk[] };
type SearchHit = { chunk: Chunk; materialId: string; materialTitle: string; materialLabels: LabelRef[];
                   similarity: number };
```

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| `GET /materials?q=&labelIds=a,b&kind=&exam=true&status=` | — | `MaterialSummary[]` (updatedAt 내림차순, labelIds 는 모두 포함) |
| `POST /materials` | multipart: `file?`(PDF·DOCX·TXT·MD, 50MB), `text?`(붙여넣기), `title`, `kind`, `description?`, `labelIds?`(쉼표), `isExam`(true/false), `autoLabel`(true/false — AI 가 기존 라벨 중 맞는 것을 청크에 붙임) | `MaterialSummary` — 바로 백그라운드 처리 시작 |
| `GET /materials/{id}` | — | `MaterialDetail` (청크 seq 순) |
| `PATCH /materials/{id}` | `{ title?, kind?, description?, labelIds?, isExam? }` | `MaterialSummary` (라벨이 바뀌면 청크 재임베딩) |
| `DELETE /materials/{id}` | — | `204` (청크·성적 함께 삭제) |
| `POST /materials/{id}/process` | `{ restructure: boolean }` | `MaterialSummary` — restructure=true 면 추출·구조화부터 다시 (성적 세트가 있으면 409), false 면 임베딩만 다시 |
| `GET /materials/{id}/file` | — | 원본 파일 |
| `POST /materials/{id}/chunks` | `Chunk` 편집 필드 (`ChunkInput`) | `Chunk` (맨 뒤에 추가, 임베딩) |
| `PATCH /chunks/{id}` | `Partial<ChunkInput>` | `Chunk` (본문·라벨이 바뀌면 재임베딩) |
| `DELETE /chunks/{id}` | — | `204` (성적이 입력된 문항이면 409) |
| `POST /search` | `{ query, labelIds?, materialIds?, chunkTypes?, examOnly?, scoredOnly?, limit? }` | `SearchHit[]` (코사인 유사도 내림차순, 기본 10개). 라벨은 같은 그룹 안 OR, 그룹끼리 AND (자료·청크 라벨 어느 쪽이든) |

```ts
type ChunkInput = { chunkType: ChunkType; title: string | null; position: string | null; itemNo: string | null;
                    questionType: QuestionType | null; content: string; choices: Choice[]; answer: string | null;
                    explanation: string | null; score: number | null; tags: string[];
                    labelIds: string[] };   // 청크 라벨 전체 교체 (source=MANUAL 로 저장)
```

처리 상태는 화면이 `GET /materials/{id}` 를 2~3초마다 다시 읽어 표시한다 (status 가 READY/FAILED 가 될 때까지).

## 3. 시험·성적

시험 자료(`isExam=true`)의 문항 = `chunkType=QUESTION` 청크 (seq 순). 배점(`score`)이 없으면 1점으로 집계한다.
성적은 **성적 세트**(응시 회차·집단 단위) 로 넣는다. 학생은 익명 키(`studentKey`, 예: 학번·번호)로만 구분한다.

```ts
type ItemStat = {
  chunkId: string; itemNo: string | null; maxScore: number;
  n: number;                            // 응답 수
  avgRate: number;                      // 정답률 = 평균 득점 ÷ 배점
  fullRate: number; zeroRate: number;
  discrimination: number | null;        // 변별도 = 총점 상위 27% 정답률 − 하위 27% 정답률 (n<10 이면 null)
  choiceDist: { response: string; count: number; correct: boolean }[];   // 응답(선택지)별 분포, RESPONSE 입력일 때
  smallSample: boolean;                 // n < 30
};
type ExamStat = {
  n: number; maxTotal: number; mean: number; std: number | null; meanRate: number;
  p10: number; p25: number; p50: number; p75: number; p90: number;          // 총점
  histogram: { from: number; to: number; count: number }[];                // 총점 비율 0~1, 10구간
};
type ScoreSet = { id: string; materialId: string; name: string; takenOn: string | null; note: string | null;
                  studentCount: number; createdAt: string };
type ExamItem = { chunkId: string; itemNo: string | null; questionType: QuestionType | null; maxScore: number;
                  answer: string | null; preview: string; stat: ItemStat | null };
type ExamSummary = { material: MaterialSummary; itemCount: number; maxTotal: number; scoreSetCount: number;
                     n: number; meanRate: number | null };
```

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| `GET /exams` | — | `ExamSummary[]` |
| `GET /exams/{materialId}` | — | `{ material: MaterialSummary, items: ExamItem[], scoreSets: ScoreSet[], stat: ExamStat \| null }` (stat·items[].stat 은 모든 세트 합산) |
| `POST /exams/{materialId}/score-sets` | `{ name, takenOn?, note? }` | `ScoreSet` |
| `PATCH /score-sets/{id}` | `{ name?, takenOn?, note? }` | `ScoreSet` |
| `DELETE /score-sets/{id}` | — | `204` |
| `GET /score-sets/{id}/entries` | — | `{ items: ExamItem[], rows: { studentKey: string, values: Record<chunkId, { score: number \| null, response: string \| null }> }[] }` |
| `PUT /score-sets/{id}/entries` | `{ mode: "SCORE" \| "RESPONSE", rows: { studentKey: string, values: Record<chunkId, string \| null> }[] }` | `{ rows: number, stat: ExamStat \| null }` — 세트 전체 교체 |
| `POST /score-sets/{id}/import` | `{ mode: "SCORE" \| "RESPONSE", text: string, replace: boolean }` | `{ imported: number, unknownColumns: string[], warnings: string[] }` |
| `GET /score-sets/{id}/stats` | — | `{ stat: ExamStat \| null, items: ItemStat[] }` (이 세트만) |

- `mode=SCORE`: 값 = 득점(숫자). `mode=RESPONSE`: 값 = 학생 응답(선택지 번호·답). 정답(`answer`)과 같으면(공백·대소문자 무시,
  `①`↔`1` 같은 원문자 숫자 동일 취급, 정답이 `,` 로 여러 개면 그중 하나) 만점 아니면 0점, 응답은 선택지 분포에 쓰인다.
- import `text`: CSV 또는 TSV(엑셀 붙여넣기). 첫 행 헤더 — 첫 열은 학생 키, 나머지 열은 문항 번호(`itemNo`).
  번호가 없는 열은 `unknownColumns` 로 돌려준다. 빈 칸은 미응답(null).

## 4. 컨텍스트 규칙 (조건별 주입)

요청마다(대화 메시지) 조건을 보고 맞는 규칙의 컨텍스트를 사용자 메시지에 `<context_rules>` 블록으로 붙인다.
시스템 프롬프트는 바뀌지 않는다 (프롬프트 캐시 유지).

```ts
type RuleField = "labels" | "outputKind" | "questionTypes" | "materials" | "audience" | "request";
type RuleClause = { field: RuleField; op: "any" | "all" | "none"; values: string[] };
// labels·materials·questionTypes·outputKind: 집합 비교 (any = 하나라도 포함, all = 모두 포함, none = 하나도 없음)
//   labels 의 values 는 라벨 id, materials 는 자료 id
// audience·request: 문자열 포함 (대소문자 무시) — request 는 사용자 요청 문장
type RuleCondition = { match: "ALL" | "ANY"; clauses: RuleClause[] };   // clauses 가 비면 항상 적용
type RuleInjection = {
  instruction: string | null;          // 지침 (마크다운)
  materialIds: string[];               // 이 자료들의 본문을 그대로 주입 (예: 출제 양식 예시, 교육과정 성취기준)
  retrieval: { query: string | null;   // null 이면 사용자 요청 문장으로 검색
               labelIds: string[]; chunkTypes: ChunkType[]; topK: number } | null;   // 라벨 범위 검색 결과 주입
  maxChars: number;                    // 이 규칙이 주입할 최대 글자 수 (기본 8000)
};
type ContextRule = { id: string; name: string; description: string | null; enabled: boolean; priority: number;
                     condition: RuleCondition; injection: RuleInjection; createdAt: string; updatedAt: string };
type AppliedRule = { id: string; name: string; materialCount: number; chunkCount: number; chars: number };
```

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| `GET /context-rules` | — | `ContextRule[]` (priority 내림차순) |
| `POST /context-rules` | `ContextRule` 편집 필드 | `ContextRule` |
| `PATCH /context-rules/{id}` | 편집 필드 일부 | `ContextRule` |
| `DELETE /context-rules/{id}` | — | `204` |
| `POST /context-rules/preview` | `{ conditions: Conditions, text: string }` | `{ applied: AppliedRule[], block: string \| null }` — 실제로 주입될 블록 |

## 5. 작업실 (대화)

```ts
type OutputKind = "EXAM" | "QUIZ" | "WORKSHEET" | "LESSON_NOTE" | "SUMMARY" | "FLASHCARDS" | "EXPLANATION" | "OTHER";
type Conditions = {
  outputKind: OutputKind | null;       // 만들 자료 종류
  labelIds: string[];                  // 참고 범위 라벨 (검색 기본 필터 + 규칙 조건)
  materialIds: string[];               // 꼭 참고할 자료
  questionTypes: QuestionType[];       // 원하는 문항 유형
  itemCount: number | null; totalScore: number | null;
  targetCorrectRate: number | null;    // 목표 정답률 0~1
  audience: string | null;             // 대상 (예: "고2 중위권")
  creativity: number | null;           // 외부 자료(웹 검색) 참조 0~4, null=0
  extra: string | null;
};
type ToolStep = { id: string; name: string; label: string; status: "started" | "done" | "error"; summary: string | null };
type Turn = {
  id: string; role: "user" | "assistant";
  text: string; thinking: string | null; tools: ToolStep[];
  context: AppliedRule[];              // assistant 턴에 주입된 규칙
  documentVersions: number[];          // 이 턴에서 만들어진 산출물 버전
  conditions: Conditions | null;       // user 턴에 함께 보낸 조건
  stopped: boolean; createdAt: string;
};
type ConversationSummary = { id: string; title: string; archived: boolean; conditions: Conditions;
                             documentId: string | null; currentVersion: number | null; costUsd: number;
                             createdAt: string; updatedAt: string };
```

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| `GET /conversations?q=&archived=false` | — | `ConversationSummary[]` |
| `POST /conversations` | `{ title?, conditions? }` | `ConversationSummary` |
| `PATCH /conversations/{id}` | `{ title?, archived?, conditions? }` | `ConversationSummary` |
| `DELETE /conversations/{id}` | — | `204` (산출물은 남는다) |
| `GET /conversations/{id}/messages` | — | `Turn[]` |
| `POST /conversations/{id}/messages` | `{ text, conditions }` | **SSE** |
| `POST /conversations/{id}/stop` | — | `204` |
| `GET /conversations/running` | — | `string[]` 생성 중인 대화 id |

SSE (`text/event-stream`, fetch 스트림으로 읽는다. 15초마다 `: ping` 주석):

| event | data |
| --- | --- |
| `meta` | `{ userTurnId, conversationId }` |
| `context` | `{ rules: AppliedRule[] }` |
| `thinking` | `{ text }` 조각 |
| `tool` | `ToolStep` |
| `text` | `{ text }` 조각 |
| `document` | `DocumentVersion` |
| `done` | `{ inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens, costUsd, conversationCostUsd, monthCostUsd, stopReason }` |
| `error` | `{ code, message }` (`BUDGET_EXCEEDED`, `REFUSED`, `AI_ERROR`, `BUSY`) |

## 6. 산출물 (문제·교육자료)

구조(`DocPayload`)가 원본이고 문제지(학생용)·정답해설지(교사용)는 그 뷰다. 섹션 본문만 있으면 학습자료, 문항이 있으면 문제지.

```ts
type Item = {
  number: string;                      // "1", "2-1"
  type: QuestionType;
  stem: string;                        // 발문 (마크다운 + LaTeX)
  choices: Choice[];                   // 객관식·OX·배열·짝짓기
  answer: string;                      // 정답 (객관식은 choice label, 복수 정답은 "①,③")
  explanation: string | null;          // 해설
  score: number | null;
  criteria: { text: string; score: number }[];   // 서술형 채점기준 (배점 합 = score)
  difficulty: number | null;           // 예상 정답률 0~1
  tags: string[];
  sourceRefs: string[];                // 참고한 청크 "chunk:<id>"
};
type Section = { title: string | null; body: string | null; items: Item[] };
type Evidence = { kind: "chunk" | "item_stat" | "exam_stat" | "rule" | "material" | "web"; ref: string; summary: string };
type DocPayload = {
  title: string; kind: OutputKind; audience: string | null;
  summary: string | null;              // 자료 개요·출제 의도
  instructions: string | null;         // 학생 안내문 / 학습 목표
  sections: Section[];
  evidence: Evidence[];
  changeNote: string | null;
};
type SimilarHit = { itemNumber: string; chunkId: string; materialId: string; materialTitle: string;
                    position: string | null; similarity: number };
type ItemPrediction = { itemNumber: string; expectedRate: number | null; confidence: "HIGH" | "MEDIUM" | "LOW" | "NONE";
                        neighbors: { chunkId: string; materialTitle: string; position: string | null;
                                     similarity: number; n: number; avgRate: number }[] };
type DocumentVersion = { documentId: string; version: number; payload: DocPayload; warnings: string[];
                         similar: SimilarHit[]; prediction: ItemPrediction[]; createdAt: string };
type DocumentSummary = { id: string; conversationId: string | null; title: string; kind: OutputKind;
                         status: "DRAFT" | "FINAL" | "ARCHIVED"; currentVersion: number; itemCount: number;
                         totalScore: number | null; maxSimilarity: number | null; updatedAt: string };
```

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| `GET /documents?q=&kind=&status=` | — | `DocumentSummary[]` |
| `POST /documents` | `DocPayload` | `DocumentVersion` — 대화 없이 직접 작성 (v1) |
| `GET /documents/{id}` | — | `DocumentSummary & { versions: DocumentVersion[] }` (버전 오름차순) |
| `PATCH /documents/{id}` | `{ title?, status? }` | `DocumentSummary` |
| `DELETE /documents/{id}` | — | `204` |
| `POST /documents/{id}/versions` | `DocPayload` | `DocumentVersion` (직접 편집 → 검증·유사도·예측 후 새 버전, 검증 실패는 400 message 에 목록) |
| `POST /documents/{id}/restore/{version}` | — | `DocumentVersion` |
| `GET /documents/{id}/export?version=&answers=true` | — | `text/markdown` 파일 |

## 7. 설정·시스템

```ts
type Settings = { model: "claude-opus-5-5" | "claude-sonnet-5-5"; effort: "low" | "medium" | "high" | "xhigh";
                  similarityWarn: number; maxToolCalls: number; monthlyBudgetUsd: number /* 읽기 전용 */ };
type Usage = { monthCostUsd: number; monthBudgetUsd: number;
               byPurpose: { purpose: "chat" | "structure" | "embedding"; costUsd: number }[];
               daily: { day: string; costUsd: number }[] };
type SystemStatus = { db: { ok: boolean; error: string | null };
                      claude: { configured: boolean; model: string };
                      embedding: { provider: "voyage" | "local"; model: string; dimension: number };
                      counts: { materials: number; chunks: number; embedded: number; labels: number; rules: number;
                                documents: number; scoreSets: number } };
```

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| `GET /settings` | — | `Settings` |
| `PUT /settings` | `Settings` | `Settings` |
| `GET /usage` | — | `Usage` |
| `GET /system/status` | — | `SystemStatus` |
| `GET /health` | — | `{ ok: true }` (인증 불필요) |
