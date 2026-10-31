import { getToken, handleUnauthorized } from './auth';
import type {
  ChunkInput,
  Chunk,
  Conditions,
  ContextRule,
  ContextRuleInput,
  ConversationSummary,
  DocPayload,
  DocumentDetail,
  DocumentStatus,
  DocumentSummary,
  DocumentVersion,
  ExamDetail,
  ExamStat,
  ExamSummary,
  ImportResult,
  ItemStat,
  Label,
  LabelInput,
  LoginResult,
  MaterialDetail,
  MaterialPatch,
  MaterialQuery,
  MaterialSummary,
  OutputKind,
  RulePreview,
  ScoreEntries,
  ScoreEntriesInput,
  ScoreMode,
  ScoreSet,
  ScoreSetInput,
  SearchHit,
  SearchInput,
  Settings,
  SystemStatus,
  Turn,
  UploadInput,
  Usage,
  User,
  UserInput,
} from './types';

export const API_BASE = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:18600/api').replace(/\/$/, '');

export class HttpError extends Error {
  status: number;
  code: string;
  constructor(status: number, code: string, message: string) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

export function errorMessage(e: unknown): string {
  if (e instanceof HttpError) return e.message;
  if (e instanceof TypeError) return '서버에 연결할 수 없습니다. 서버가 켜져 있는지 확인하세요.';
  if (e instanceof Error) return e.message;
  return '알 수 없는 오류가 발생했습니다';
}

type Query = Record<string, string | number | boolean | null | undefined>;

export function buildUrl(path: string, query?: Query): string {
  const url = `${API_BASE}${path}`;
  if (!query) return url;
  const params = new URLSearchParams();
  for (const [k, v] of Object.entries(query)) {
    if (v === undefined || v === null || v === '') continue;
    params.set(k, String(v));
  }
  const qs = params.toString();
  return qs ? `${url}?${qs}` : url;
}

export function authHeaders(extra?: Record<string, string>): Record<string, string> {
  const headers: Record<string, string> = { ...extra };
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  return headers;
}

export async function toHttpError(res: Response): Promise<HttpError> {
  let body: Record<string, unknown> = {};
  try {
    body = (await res.json()) as Record<string, unknown>;
  } catch {
    // 본문이 JSON 이 아니면 상태 코드로만 안내
  }
  const code = typeof body.code === 'string' ? body.code : `HTTP_${res.status}`;
  const message = typeof body.message === 'string' ? body.message : `요청을 처리하지 못했습니다 (${res.status})`;
  return new HttpError(res.status, code, message);
}

type RequestOptions = { method?: string; body?: unknown; query?: Query; form?: FormData; auth?: boolean };

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, query, form, auth = true } = opts;
  const headers: Record<string, string> = auth ? authHeaders() : {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(buildUrl(path, query), {
    method,
    headers,
    body: form ?? (body === undefined ? undefined : JSON.stringify(body)),
  });
  if (res.status === 401 && auth) {
    handleUnauthorized();
    throw await toHttpError(res);
  }
  if (!res.ok) throw await toHttpError(res);
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** 인증 헤더를 붙여 파일을 내려받는다 */
export async function download(path: string, query: Query, fallbackName: string) {
  const res = await fetch(buildUrl(path, query), { headers: authHeaders() });
  if (res.status === 401) {
    handleUnauthorized();
    throw await toHttpError(res);
  }
  if (!res.ok) throw await toHttpError(res);
  const blob = await res.blob();
  const disposition = res.headers.get('Content-Disposition') || '';
  const match = /filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/i.exec(disposition);
  const name = match ? decodeURIComponent(match[1] || match[2]) : fallbackName;
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

const csv = (ids?: string[]) => (ids && ids.length ? ids.join(',') : undefined);

export const api = {
  // 0. 인증·사용자
  login: (email: string, password: string) =>
    request<LoginResult>('/auth/login', { method: 'POST', body: { email, password }, auth: false }),
  logout: () => request<void>('/auth/logout', { method: 'POST' }),
  me: () => request<User>('/auth/me'),
  users: () => request<User[]>('/users'),
  createUser: (body: UserInput) => request<User>('/users', { method: 'POST', body }),
  deleteUser: (id: string) => request<void>(`/users/${id}`, { method: 'DELETE' }),

  // 1. 라벨
  labels: () => request<Label[]>('/labels'),
  createLabel: (body: LabelInput) => request<Label>('/labels', { method: 'POST', body }),
  updateLabel: (id: string, body: Partial<LabelInput>) => request<Label>(`/labels/${id}`, { method: 'PATCH', body }),
  deleteLabel: (id: string) => request<void>(`/labels/${id}`, { method: 'DELETE' }),

  // 2. 자료
  materials: (q: MaterialQuery = {}) =>
    request<MaterialSummary[]>('/materials', {
      query: { q: q.q, labelIds: csv(q.labelIds), kind: q.kind, exam: q.exam ? true : undefined, status: q.status },
    }),
  uploadMaterial: (input: UploadInput) => {
    const form = new FormData();
    if (input.file) form.append('file', input.file);
    if (input.text) form.append('text', input.text);
    form.append('title', input.title);
    form.append('kind', input.kind);
    if (input.description) form.append('description', input.description);
    if (input.labelIds.length) form.append('labelIds', input.labelIds.join(','));
    form.append('isExam', String(input.isExam));
    form.append('autoLabel', String(input.autoLabel));
    return request<MaterialSummary>('/materials', { method: 'POST', form });
  },
  material: (id: string) => request<MaterialDetail>(`/materials/${id}`),
  updateMaterial: (id: string, body: MaterialPatch) =>
    request<MaterialSummary>(`/materials/${id}`, { method: 'PATCH', body }),
  deleteMaterial: (id: string) => request<void>(`/materials/${id}`, { method: 'DELETE' }),
  processMaterial: (id: string, restructure: boolean) =>
    request<MaterialSummary>(`/materials/${id}/process`, { method: 'POST', body: { restructure } }),
  downloadMaterialFile: (id: string, name: string) => download(`/materials/${id}/file`, {}, name),
  addChunk: (materialId: string, body: ChunkInput) =>
    request<Chunk>(`/materials/${materialId}/chunks`, { method: 'POST', body }),
  updateChunk: (id: string, body: Partial<ChunkInput>) => request<Chunk>(`/chunks/${id}`, { method: 'PATCH', body }),
  deleteChunk: (id: string) => request<void>(`/chunks/${id}`, { method: 'DELETE' }),
  search: (body: SearchInput) => request<SearchHit[]>('/search', { method: 'POST', body }),

  // 3. 시험·성적
  exams: () => request<ExamSummary[]>('/exams'),
  exam: (materialId: string) => request<ExamDetail>(`/exams/${materialId}`),
  createScoreSet: (materialId: string, body: ScoreSetInput) =>
    request<ScoreSet>(`/exams/${materialId}/score-sets`, { method: 'POST', body }),
  updateScoreSet: (id: string, body: Partial<ScoreSetInput>) =>
    request<ScoreSet>(`/score-sets/${id}`, { method: 'PATCH', body }),
  deleteScoreSet: (id: string) => request<void>(`/score-sets/${id}`, { method: 'DELETE' }),
  scoreEntries: (id: string) => request<ScoreEntries>(`/score-sets/${id}/entries`),
  saveScoreEntries: (id: string, body: ScoreEntriesInput) =>
    request<{ rows: number; stat: ExamStat | null }>(`/score-sets/${id}/entries`, { method: 'PUT', body }),
  importScores: (id: string, body: { mode: ScoreMode; text: string; replace: boolean }) =>
    request<ImportResult>(`/score-sets/${id}/import`, { method: 'POST', body }),
  scoreSetStats: (id: string) => request<{ stat: ExamStat | null; items: ItemStat[] }>(`/score-sets/${id}/stats`),

  // 4. 컨텍스트 규칙
  rules: () => request<ContextRule[]>('/context-rules'),
  createRule: (body: ContextRuleInput) => request<ContextRule>('/context-rules', { method: 'POST', body }),
  updateRule: (id: string, body: Partial<ContextRuleInput>) =>
    request<ContextRule>(`/context-rules/${id}`, { method: 'PATCH', body }),
  deleteRule: (id: string) => request<void>(`/context-rules/${id}`, { method: 'DELETE' }),
  previewRules: (conditions: Conditions, text: string) =>
    request<RulePreview>('/context-rules/preview', { method: 'POST', body: { conditions, text } }),

  // 5. 작업실
  conversations: (q?: string, archived = false) =>
    request<ConversationSummary[]>('/conversations', { query: { q, archived } }),
  createConversation: (body: { title?: string; conditions?: Conditions }) =>
    request<ConversationSummary>('/conversations', { method: 'POST', body }),
  updateConversation: (id: string, body: { title?: string; archived?: boolean; conditions?: Conditions }) =>
    request<ConversationSummary>(`/conversations/${id}`, { method: 'PATCH', body }),
  deleteConversation: (id: string) => request<void>(`/conversations/${id}`, { method: 'DELETE' }),
  messages: (id: string) => request<Turn[]>(`/conversations/${id}/messages`),
  stop: (id: string) => request<void>(`/conversations/${id}/stop`, { method: 'POST' }),
  runningConversations: () => request<string[]>('/conversations/running'),

  // 6. 산출물
  documents: (q: { q?: string; kind?: OutputKind | ''; status?: DocumentStatus | '' } = {}) =>
    request<DocumentSummary[]>('/documents', { query: q }),
  createDocument: (payload: DocPayload) => request<DocumentVersion>('/documents', { method: 'POST', body: payload }),
  document: (id: string) => request<DocumentDetail>(`/documents/${id}`),
  updateDocument: (id: string, body: { title?: string; status?: DocumentStatus }) =>
    request<DocumentSummary>(`/documents/${id}`, { method: 'PATCH', body }),
  deleteDocument: (id: string) => request<void>(`/documents/${id}`, { method: 'DELETE' }),
  saveVersion: (id: string, payload: DocPayload) =>
    request<DocumentVersion>(`/documents/${id}/versions`, { method: 'POST', body: payload }),
  restoreVersion: (id: string, version: number) =>
    request<DocumentVersion>(`/documents/${id}/restore/${version}`, { method: 'POST' }),
  exportDocument: (id: string, version: number, answers: boolean, name: string) =>
    download(`/documents/${id}/export`, { version, answers }, name),

  // 7. 설정·시스템
  settings: () => request<Settings>('/settings'),
  saveSettings: (body: Settings) => request<Settings>('/settings', { method: 'PUT', body }),
  usage: () => request<Usage>('/usage'),
  systemStatus: () => request<SystemStatus>('/system/status'),
};
