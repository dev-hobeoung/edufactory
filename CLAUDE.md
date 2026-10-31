# CLAUDE.md

구조·실행은 `README.md`, API 계약은 `docs/API.md`. API 를 바꾸면 계약서와 `web/src/api/types.ts` 를 함께 고친다.

## 검증

```bash
cd server && ./gradlew test                 # 순수 로직 단위 테스트
cd web && npx tsc --noEmit && npm run build
```

서버를 띄운 E2E 확인은 로컬 DB(컨테이너 `edufactory`, localhost:6432)로 한다. 다른 프로젝트 DB 컨테이너는 건드리지 않는다.

## 서버 구조 (`com.hobeoung.edufactory`)

도메인 패키지별로 Controller → Service → Repository(NamedParameterJdbcTemplate, JPA 없음).
`auth` `label` `material`(+`ingest` 추출·구조화) `embedding` `exam`(성적·통계) `context`(규칙) `studio`(대화·에이전트·도구)
`document`(산출물·검증·유사도·예측) `system` `usage`.

## 지켜야 할 것

- 스키마는 Flyway(`server/src/main/resources/db/migration`). 적용된 마이그레이션은 고치지 말고 새 버전을 추가한다.
  `similar` 처럼 PostgreSQL 예약어를 컬럼 이름으로 쓰지 않는다.
- 대화 기록(`ef_message.content`)은 이어 붙이기만 한다 (과거 메시지를 고치면 thinking 블록이 무효).
- 시스템 프롬프트(`studio/Prompts`)·도구 정의에는 날짜·요청별 값을 넣지 않는다 (프롬프트 캐시). 조건·규칙 컨텍스트·산출물 상태는
  사용자 메시지(`<conditions>`·`<context_rules>`·`<document_state>`)로 보낸다. 도구는 이름 순으로 고정(`ToolRegistry`).
- `AgentService` 의 `thinking.block_binding.prefix_mismatch_behavior=drop_block`(베타 `thinking-binding-controls-2026-08-01`),
  연속 assistant 병합(`forRequest`, pause_turn), `ToolInputCompleter`, `SseSink` 15초 ping 은 academy-solution factory 에서
  운영 중 겪은 문제의 해결책이다 — 빼지 말 것.
- `submit_document` 처럼 깊게 중첩된 도구 스키마에는 `strict: true` 를 쓰지 않는다 (문법 크기 한도). `DocPayload.validate()` 로 검증.
- 임베딩 모델이 다른 벡터는 섞지 않는다 (`ef_chunk.embedding_model` 로 걸러 검색, 기동 시 다른 모델 청크는 재임베딩).
