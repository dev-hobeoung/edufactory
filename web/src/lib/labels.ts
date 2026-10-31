import type {
  ChunkType,
  Confidence,
  DocumentStatus,
  MaterialKind,
  MaterialStatus,
  OutputKind,
  QuestionType,
  RuleField,
  RuleOp,
} from '../api/types';

export const MATERIAL_KIND_LABEL: Record<MaterialKind, string> = {
  EXAM: '시험지',
  WORKSHEET: '학습지',
  TEXTBOOK: '교재',
  NOTE: '강의 노트',
  REFERENCE: '참고 자료',
  OTHER: '기타',
};
export const MATERIAL_KINDS = Object.keys(MATERIAL_KIND_LABEL) as MaterialKind[];

export const MATERIAL_STATUS_LABEL: Record<MaterialStatus, string> = {
  QUEUED: '대기',
  EXTRACTING: '텍스트 추출 중',
  STRUCTURING: '구조화 중',
  EMBEDDING: '임베딩 중',
  READY: '준비됨',
  FAILED: '실패',
};
export const MATERIAL_STATUSES = Object.keys(MATERIAL_STATUS_LABEL) as MaterialStatus[];
export const PROCESSING_STATUSES: MaterialStatus[] = ['QUEUED', 'EXTRACTING', 'STRUCTURING', 'EMBEDDING'];
export const STATUS_TONE: Record<MaterialStatus, string> = {
  QUEUED: 'bg-slate-100 text-slate-600',
  EXTRACTING: 'bg-sky-50 text-sky-700',
  STRUCTURING: 'bg-indigo-50 text-indigo-700',
  EMBEDDING: 'bg-violet-50 text-violet-700',
  READY: 'bg-emerald-50 text-emerald-700',
  FAILED: 'bg-red-50 text-red-700',
};

export const CHUNK_TYPE_LABEL: Record<ChunkType, string> = {
  QUESTION: '문항',
  PASSAGE: '지문',
  CONCEPT: '개념',
  EXPLANATION: '해설',
  EXAMPLE: '예제',
  SECTION: '단락',
  OTHER: '기타',
};
export const CHUNK_TYPES = Object.keys(CHUNK_TYPE_LABEL) as ChunkType[];
export const CHUNK_TYPE_TONE: Record<ChunkType, string> = {
  QUESTION: 'bg-indigo-50 text-indigo-700',
  PASSAGE: 'bg-amber-50 text-amber-700',
  CONCEPT: 'bg-emerald-50 text-emerald-700',
  EXPLANATION: 'bg-sky-50 text-sky-700',
  EXAMPLE: 'bg-violet-50 text-violet-700',
  SECTION: 'bg-slate-100 text-slate-600',
  OTHER: 'bg-slate-100 text-slate-500',
};

export const QUESTION_TYPE_LABEL: Record<QuestionType, string> = {
  MULTIPLE_CHOICE: '객관식',
  MULTI_SELECT: '복수 선택',
  TRUE_FALSE: 'OX',
  SHORT_ANSWER: '단답형',
  FILL_BLANK: '빈칸',
  ESSAY: '서술형',
  MATCHING: '짝짓기',
  ORDERING: '순서 배열',
  FLASHCARD: '플래시카드',
};
export const QUESTION_TYPES = Object.keys(QUESTION_TYPE_LABEL) as QuestionType[];
/** 선택지를 쓰는 유형 */
export const CHOICE_TYPES: QuestionType[] = ['MULTIPLE_CHOICE', 'MULTI_SELECT', 'TRUE_FALSE', 'MATCHING', 'ORDERING'];

export const OUTPUT_KIND_LABEL: Record<OutputKind, string> = {
  EXAM: '시험지',
  QUIZ: '퀴즈',
  WORKSHEET: '학습지',
  LESSON_NOTE: '수업 자료',
  SUMMARY: '요약 정리',
  FLASHCARDS: '플래시카드',
  EXPLANATION: '해설 자료',
  OTHER: '기타',
};
export const OUTPUT_KINDS = Object.keys(OUTPUT_KIND_LABEL) as OutputKind[];

export const DOC_STATUS_LABEL: Record<DocumentStatus, string> = { DRAFT: '초안', FINAL: '확정', ARCHIVED: '보관' };
export const DOC_STATUS_TONE: Record<DocumentStatus, string> = {
  DRAFT: 'bg-slate-100 text-slate-600',
  FINAL: 'bg-emerald-50 text-emerald-700',
  ARCHIVED: 'bg-slate-100 text-slate-400',
};

export const CONFIDENCE_LABEL: Record<Confidence, string> = { HIGH: '높음', MEDIUM: '보통', LOW: '낮음', NONE: '근거 없음' };

export const RULE_FIELD_LABEL: Record<RuleField, string> = {
  labels: '라벨',
  outputKind: '산출물 종류',
  questionTypes: '문항 유형',
  materials: '참고 자료',
  audience: '대상',
  request: '요청 문장',
};
export const RULE_FIELDS = Object.keys(RULE_FIELD_LABEL) as RuleField[];
export const RULE_OP_LABEL: Record<RuleOp, string> = { any: '하나라도 포함', all: '모두 포함', none: '포함하지 않음' };
export const TEXT_FIELDS: RuleField[] = ['audience', 'request'];

export const CREATIVITY_LEVELS = ['자료만', '가볍게', '적극', '외부 중심', '최대'];
export const CREATIVITY_HINTS = [
  '등록한 자료만 근거로 만듭니다. 웹 검색을 쓰지 않습니다.',
  '자료로 구조를 잡고, 사실·수치 확인에 웹 검색을 1~2회 씁니다.',
  '소재를 외부에서 찾아 자료에서 검증된 문항 구조에 얹습니다.',
  '여러 분야를 검색해 자료에 없던 소재로 새로 설계합니다.',
  '응용·최신 소재까지 넓게 찾아 새로운 유형을 시도합니다.',
];

export const DEFAULT_LABEL_COLOR = '#6366f1';
export const LABEL_COLORS = ['#6366f1', '#0ea5e9', '#10b981', '#f59e0b', '#ef4444', '#ec4899', '#8b5cf6', '#64748b'];
