-- EDU FACTORY 초기 스키마. 테이블은 edufactory 스키마, 확장(vector)은 public 에 둔다.
-- 임베딩 차원(1024)을 바꾸면 ef_chunk.embedding 을 다시 만들고 전체 재임베딩해야 한다.

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;

CREATE TABLE ef_user (
  id            uuid PRIMARY KEY,
  email         text NOT NULL UNIQUE,
  name          text NOT NULL,
  password_hash text NOT NULL,
  role          text NOT NULL DEFAULT 'EDITOR',
  created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ef_session (
  token      text PRIMARY KEY,
  user_id    uuid NOT NULL REFERENCES ef_user (id) ON DELETE CASCADE,
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ef_label (
  id          uuid PRIMARY KEY,
  group_name  text NOT NULL,
  name        text NOT NULL,
  color       text,
  description text,
  created_at  timestamptz NOT NULL DEFAULT now(),
  UNIQUE (group_name, name)
);

-- 자료: 업로드 원본 → 추출 텍스트(raw_text) → 청크
CREATE TABLE ef_material (
  id             uuid PRIMARY KEY,
  title          text NOT NULL,
  kind           text NOT NULL,
  description    text,
  source_type    text NOT NULL,
  file_name      text,
  content_type   text,
  file_size      bigint,
  file_key       text,
  raw_text       text,
  is_exam        boolean NOT NULL DEFAULT false,
  auto_label     boolean NOT NULL DEFAULT true,
  status         text NOT NULL,
  status_message text,
  error          text,
  structured_by  text,
  created_by     uuid REFERENCES ef_user (id) ON DELETE SET NULL,
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ef_material_label (
  material_id uuid NOT NULL REFERENCES ef_material (id) ON DELETE CASCADE,
  label_id    uuid NOT NULL REFERENCES ef_label (id) ON DELETE CASCADE,
  PRIMARY KEY (material_id, label_id)
);

-- 청크: 구조화 단위 (문항·지문·개념·해설 …). 시험 자료의 문항은 chunk_type = 'QUESTION'
CREATE TABLE ef_chunk (
  id              uuid PRIMARY KEY,
  material_id     uuid NOT NULL REFERENCES ef_material (id) ON DELETE CASCADE,
  seq             int NOT NULL,
  chunk_type      text NOT NULL,
  title           text,
  position        text,
  item_no         text,
  question_type   text,
  content         text NOT NULL,
  choices         jsonb NOT NULL DEFAULT '[]',
  answer          text,
  explanation     text,
  score           numeric,
  tags            jsonb NOT NULL DEFAULT '[]',
  page_from       int,
  page_to         int,
  embedding       public.vector(1024),
  embedding_model text,
  embedded_at     timestamptz,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ef_chunk_material_idx ON ef_chunk (material_id, seq);
CREATE INDEX ef_chunk_embedding_idx ON ef_chunk USING hnsw (embedding public.vector_cosine_ops);

CREATE TABLE ef_chunk_label (
  chunk_id uuid NOT NULL REFERENCES ef_chunk (id) ON DELETE CASCADE,
  label_id uuid NOT NULL REFERENCES ef_label (id) ON DELETE CASCADE,
  source   text NOT NULL DEFAULT 'MANUAL',
  PRIMARY KEY (chunk_id, label_id)
);

-- 성적: 세트(응시 회차·집단) × 학생 × 문항
CREATE TABLE ef_score_set (
  id          uuid PRIMARY KEY,
  material_id uuid NOT NULL REFERENCES ef_material (id) ON DELETE CASCADE,
  name        text NOT NULL,
  taken_on    date,
  note        text,
  created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ef_score_entry (
  score_set_id uuid NOT NULL REFERENCES ef_score_set (id) ON DELETE CASCADE,
  student_key  text NOT NULL,
  chunk_id     uuid NOT NULL REFERENCES ef_chunk (id) ON DELETE CASCADE,
  score        numeric,
  response     text,
  PRIMARY KEY (score_set_id, student_key, chunk_id)
);
CREATE INDEX ef_score_entry_chunk_idx ON ef_score_entry (chunk_id);

-- 모든 세트 합산 통계 (성적 저장 때 다시 계산). 도구·검색이 조인해서 쓴다
CREATE TABLE ef_item_stat (
  chunk_id   uuid PRIMARY KEY REFERENCES ef_chunk (id) ON DELETE CASCADE,
  n          int NOT NULL,
  avg_rate   double precision NOT NULL,
  stat       jsonb NOT NULL,
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ef_exam_stat (
  material_id uuid PRIMARY KEY REFERENCES ef_material (id) ON DELETE CASCADE,
  stat        jsonb NOT NULL,
  updated_at  timestamptz NOT NULL DEFAULT now()
);

-- 컨텍스트 규칙: 조건이 맞으면 지침·자료·검색 결과를 요청에 주입
CREATE TABLE ef_context_rule (
  id          uuid PRIMARY KEY,
  name        text NOT NULL,
  description text,
  enabled     boolean NOT NULL DEFAULT true,
  priority    int NOT NULL DEFAULT 0,
  condition   jsonb NOT NULL,
  injection   jsonb NOT NULL,
  created_at  timestamptz NOT NULL DEFAULT now(),
  updated_at  timestamptz NOT NULL DEFAULT now()
);

-- 작업실 대화. 메시지 content 는 Claude 에 보낸 그대로(MessageParam) 이어 붙이기만 한다
CREATE TABLE ef_conversation (
  id         uuid PRIMARY KEY,
  title      text NOT NULL,
  archived   boolean NOT NULL DEFAULT false,
  conditions jsonb NOT NULL DEFAULT '{}',
  cost_usd   double precision NOT NULL DEFAULT 0,
  created_by uuid REFERENCES ef_user (id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ef_message (
  id                 uuid PRIMARY KEY,
  seq                bigint GENERATED ALWAYS AS IDENTITY,
  conversation_id    uuid NOT NULL REFERENCES ef_conversation (id) ON DELETE CASCADE,
  role               text NOT NULL,
  kind               text NOT NULL,
  content            jsonb NOT NULL,
  display            jsonb NOT NULL,
  model              text,
  input_tokens       bigint,
  output_tokens      bigint,
  cache_read_tokens  bigint,
  cache_write_tokens bigint,
  cost_usd           double precision,
  created_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ef_message_conversation_idx ON ef_message (conversation_id, seq);

-- 산출물(문제·교육자료). 대화당 하나, 버전으로 쌓인다
CREATE TABLE ef_document (
  id              uuid PRIMARY KEY,
  conversation_id uuid REFERENCES ef_conversation (id) ON DELETE SET NULL,
  title           text NOT NULL,
  kind            text NOT NULL,
  status          text NOT NULL DEFAULT 'DRAFT',
  current_version int NOT NULL DEFAULT 0,
  created_by      uuid REFERENCES ef_user (id) ON DELETE SET NULL,
  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ef_document_conversation_idx ON ef_document (conversation_id) WHERE conversation_id IS NOT NULL;

CREATE TABLE ef_document_version (
  document_id    uuid NOT NULL REFERENCES ef_document (id) ON DELETE CASCADE,
  version        int NOT NULL,
  payload        jsonb NOT NULL,
  warnings       jsonb NOT NULL DEFAULT '[]',
  similar_hits   jsonb NOT NULL DEFAULT '[]',
  prediction     jsonb NOT NULL DEFAULT '[]',
  max_similarity double precision,
  message_id     uuid,
  created_at     timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (document_id, version)
);

CREATE TABLE ef_usage (
  day                date NOT NULL,
  purpose            text NOT NULL,
  model              text NOT NULL,
  input_tokens       bigint NOT NULL DEFAULT 0,
  output_tokens      bigint NOT NULL DEFAULT 0,
  cache_read_tokens  bigint NOT NULL DEFAULT 0,
  cache_write_tokens bigint NOT NULL DEFAULT 0,
  web_searches       bigint NOT NULL DEFAULT 0,
  cost_usd           double precision NOT NULL DEFAULT 0,
  PRIMARY KEY (day, purpose, model)
);

CREATE TABLE ef_setting (
  key   text PRIMARY KEY,
  value jsonb NOT NULL
);
