// docs/API.md 계약 그대로

// ── 0. 인증 ──
export type Role = 'ADMIN' | 'EDITOR';
export type User = { id: string; email: string; name: string; role: Role };
export type LoginResult = { token: string; user: User };
export type UserInput = { email: string; name: string; password: string; role: Role };

// ── 1. 라벨 ──
export type Label = {
  id: string;
  group: string;
  name: string;
  color: string | null;
  description: string | null;
  materialCount: number;
  chunkCount: number;
};
export type LabelRef = { id: string; group: string; name: string; color: string | null };
export type LabelInput = { group: string; name: string; color?: string | null; description?: string | null };

// ── 2. 자료 ──
export type MaterialKind = 'EXAM' | 'WORKSHEET' | 'TEXTBOOK' | 'NOTE' | 'REFERENCE' | 'OTHER';
export type MaterialStatus = 'QUEUED' | 'EXTRACTING' | 'STRUCTURING' | 'EMBEDDING' | 'READY' | 'FAILED';
export type SourceType = 'PDF' | 'DOCX' | 'TEXT' | 'MARKDOWN' | 'PASTE';
export type ChunkType = 'QUESTION' | 'PASSAGE' | 'CONCEPT' | 'EXPLANATION' | 'EXAMPLE' | 'SECTION' | 'OTHER';
export type QuestionType =
  | 'MULTIPLE_CHOICE'
  | 'MULTI_SELECT'
  | 'TRUE_FALSE'
  | 'SHORT_ANSWER'
  | 'FILL_BLANK'
  | 'ESSAY'
  | 'MATCHING'
  | 'ORDERING'
  | 'FLASHCARD';

export type MaterialSummary = {
  id: string;
  title: string;
  kind: MaterialKind;
  description: string | null;
  sourceType: SourceType;
  fileName: string | null;
  fileSize: number | null;
  isExam: boolean;
  status: MaterialStatus;
  statusMessage: string | null;
  error: string | null;
  labels: LabelRef[];
  chunkCount: number;
  embeddedCount: number;
  scoreSetCount: number;
  structuredBy: 'AI' | 'RULE' | null;
  createdAt: string;
  updatedAt: string;
};
export type Choice = { label: string; text: string };
export type ChunkLabel = LabelRef & { source: 'MANUAL' | 'AI' };
export type Chunk = {
  id: string;
  materialId: string;
  seq: number;
  chunkType: ChunkType;
  title: string | null;
  position: string | null;
  itemNo: string | null;
  questionType: QuestionType | null;
  content: string;
  choices: Choice[];
  answer: string | null;
  explanation: string | null;
  score: number | null;
  tags: string[];
  pageFrom: number | null;
  pageTo: number | null;
  labels: ChunkLabel[];
  embedded: boolean;
  stat: ItemStat | null;
};
export type MaterialDetail = MaterialSummary & { chunks: Chunk[] };
export type SearchHit = {
  chunk: Chunk;
  materialId: string;
  materialTitle: string;
  materialLabels: LabelRef[];
  similarity: number;
};
export type ChunkInput = {
  chunkType: ChunkType;
  title: string | null;
  position: string | null;
  itemNo: string | null;
  questionType: QuestionType | null;
  content: string;
  choices: Choice[];
  answer: string | null;
  explanation: string | null;
  score: number | null;
  tags: string[];
  labelIds: string[];
};
export type MaterialPatch = {
  title?: string;
  kind?: MaterialKind;
  description?: string | null;
  labelIds?: string[];
  isExam?: boolean;
};
export type MaterialQuery = {
  q?: string;
  labelIds?: string[];
  kind?: MaterialKind | '';
  exam?: boolean;
  status?: MaterialStatus | '';
};
export type UploadInput = {
  file: File | null;
  text: string | null;
  title: string;
  kind: MaterialKind;
  description: string | null;
  labelIds: string[];
  isExam: boolean;
  autoLabel: boolean;
};
export type SearchInput = {
  query: string;
  labelIds?: string[];
  materialIds?: string[];
  chunkTypes?: ChunkType[];
  examOnly?: boolean;
  limit?: number;
};

// ── 3. 시험·성적 ──
export type ItemStat = {
  chunkId: string;
  itemNo: string | null;
  maxScore: number;
  n: number;
  avgRate: number;
  fullRate: number;
  zeroRate: number;
  discrimination: number | null;
  choiceDist: { response: string; count: number; correct: boolean }[];
  smallSample: boolean;
};
export type ExamStat = {
  n: number;
  maxTotal: number;
  mean: number;
  std: number | null;
  meanRate: number;
  p10: number;
  p25: number;
  p50: number;
  p75: number;
  p90: number;
  histogram: { from: number; to: number; count: number }[];
};
export type ScoreSet = {
  id: string;
  materialId: string;
  name: string;
  takenOn: string | null;
  note: string | null;
  studentCount: number;
  createdAt: string;
};
export type ScoreSetInput = { name: string; takenOn?: string | null; note?: string | null };
export type ExamItem = {
  chunkId: string;
  itemNo: string | null;
  questionType: QuestionType | null;
  maxScore: number;
  answer: string | null;
  preview: string;
  stat: ItemStat | null;
};
export type ExamSummary = {
  material: MaterialSummary;
  itemCount: number;
  maxTotal: number;
  scoreSetCount: number;
  n: number;
  meanRate: number | null;
};
export type ExamDetail = { material: MaterialSummary; items: ExamItem[]; scoreSets: ScoreSet[]; stat: ExamStat | null };
export type ScoreMode = 'SCORE' | 'RESPONSE';
export type ScoreEntries = {
  items: ExamItem[];
  rows: { studentKey: string; values: Record<string, { score: number | null; response: string | null }> }[];
};
export type ScoreEntriesInput = {
  mode: ScoreMode;
  rows: { studentKey: string; values: Record<string, string | null> }[];
};
export type ImportResult = { imported: number; unknownColumns: string[]; warnings: string[] };

// ── 4. 컨텍스트 규칙 ──
export type RuleField = 'labels' | 'outputKind' | 'questionTypes' | 'materials' | 'audience' | 'request';
export type RuleOp = 'any' | 'all' | 'none';
export type RuleClause = { field: RuleField; op: RuleOp; values: string[] };
export type RuleCondition = { match: 'ALL' | 'ANY'; clauses: RuleClause[] };
export type RuleRetrieval = { query: string | null; labelIds: string[]; chunkTypes: ChunkType[]; topK: number };
export type RuleInjection = {
  instruction: string | null;
  materialIds: string[];
  retrieval: RuleRetrieval | null;
  maxChars: number;
};
export type ContextRule = {
  id: string;
  name: string;
  description: string | null;
  enabled: boolean;
  priority: number;
  condition: RuleCondition;
  injection: RuleInjection;
  createdAt: string;
  updatedAt: string;
};
export type ContextRuleInput = Omit<ContextRule, 'id' | 'createdAt' | 'updatedAt'>;
export type AppliedRule = { id: string; name: string; materialCount: number; chunkCount: number; chars: number };
export type RulePreview = { applied: AppliedRule[]; block: string | null };

// ── 5. 작업실 ──
export type OutputKind = 'EXAM' | 'QUIZ' | 'WORKSHEET' | 'LESSON_NOTE' | 'SUMMARY' | 'FLASHCARDS' | 'EXPLANATION' | 'OTHER';
export type Conditions = {
  outputKind: OutputKind | null;
  labelIds: string[];
  materialIds: string[];
  questionTypes: QuestionType[];
  itemCount: number | null;
  totalScore: number | null;
  targetCorrectRate: number | null;
  audience: string | null;
  creativity: number | null;
  extra: string | null;
};
export const EMPTY_CONDITIONS: Conditions = {
  outputKind: null,
  labelIds: [],
  materialIds: [],
  questionTypes: [],
  itemCount: null,
  totalScore: null,
  targetCorrectRate: null,
  audience: null,
  creativity: null,
  extra: null,
};

/** 서버가 일부 필드를 빼고 보내도 화면이 깨지지 않게 채운다 */
export function normalizeConditions(c: Partial<Conditions> | null | undefined): Conditions {
  return {
    ...EMPTY_CONDITIONS,
    ...(c ?? {}),
    labelIds: c?.labelIds ?? [],
    materialIds: c?.materialIds ?? [],
    questionTypes: c?.questionTypes ?? [],
  };
}

export type ToolStep = {
  id: string;
  name: string;
  label: string;
  status: 'started' | 'done' | 'error';
  summary: string | null;
};
export type Turn = {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  thinking: string | null;
  tools: ToolStep[];
  context: AppliedRule[];
  documentVersions: number[];
  conditions: Conditions | null;
  stopped: boolean;
  createdAt: string;
};
export type ConversationSummary = {
  id: string;
  title: string;
  archived: boolean;
  conditions: Conditions;
  documentId: string | null;
  currentVersion: number | null;
  costUsd: number;
  createdAt: string;
  updatedAt: string;
};
export type DonePayload = {
  inputTokens: number;
  outputTokens: number;
  cacheReadTokens: number;
  cacheWriteTokens: number;
  costUsd: number;
  conversationCostUsd: number;
  monthCostUsd: number;
  stopReason: string;
};

// ── 6. 산출물 ──
export type Criterion = { text: string; score: number };
export type Item = {
  number: string;
  type: QuestionType;
  stem: string;
  choices: Choice[];
  answer: string;
  explanation: string | null;
  score: number | null;
  criteria: Criterion[];
  difficulty: number | null;
  tags: string[];
  sourceRefs: string[];
};
export type Section = { title: string | null; body: string | null; items: Item[] };
export type EvidenceKind = 'chunk' | 'item_stat' | 'exam_stat' | 'rule' | 'material' | 'web';
export type Evidence = { kind: EvidenceKind; ref: string; summary: string };
export type DocPayload = {
  title: string;
  kind: OutputKind;
  audience: string | null;
  summary: string | null;
  instructions: string | null;
  sections: Section[];
  evidence: Evidence[];
  changeNote: string | null;
};
export type SimilarHit = {
  itemNumber: string;
  chunkId: string;
  materialId: string;
  materialTitle: string;
  position: string | null;
  similarity: number;
};
export type Confidence = 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE';
export type ItemPrediction = {
  itemNumber: string;
  expectedRate: number | null;
  confidence: Confidence;
  neighbors: {
    chunkId: string;
    materialTitle: string;
    position: string | null;
    similarity: number;
    n: number;
    avgRate: number;
  }[];
};
export type DocumentVersion = {
  documentId: string;
  version: number;
  payload: DocPayload;
  warnings: string[];
  similar: SimilarHit[];
  prediction: ItemPrediction[];
  createdAt: string;
};
export type DocumentStatus = 'DRAFT' | 'FINAL' | 'ARCHIVED';
export type DocumentSummary = {
  id: string;
  conversationId: string | null;
  title: string;
  kind: OutputKind;
  status: DocumentStatus;
  currentVersion: number;
  itemCount: number;
  totalScore: number | null;
  maxSimilarity: number | null;
  updatedAt: string;
};
export type DocumentDetail = DocumentSummary & { versions: DocumentVersion[] };

// ── 7. 설정·시스템 ──
export type Settings = {
  model: 'claude-opus-5-5' | 'claude-sonnet-5-5';
  effort: 'low' | 'medium' | 'high' | 'xhigh';
  similarityWarn: number;
  maxToolCalls: number;
  monthlyBudgetUsd: number;
};
export type Usage = {
  monthCostUsd: number;
  monthBudgetUsd: number;
  byPurpose: { purpose: 'chat' | 'structure' | 'embedding'; costUsd: number }[];
  daily: { day: string; costUsd: number }[];
};
export type SystemStatus = {
  db: { ok: boolean; error: string | null };
  claude: { configured: boolean; model: string };
  embedding: { provider: 'voyage' | 'local'; model: string; dimension: number };
  counts: {
    materials: number;
    chunks: number;
    embedded: number;
    labels: number;
    rules: number;
    documents: number;
    scoreSets: number;
  };
};
