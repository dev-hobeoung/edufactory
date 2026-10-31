import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api, errorMessage, HttpError } from '../../api/client';
import { streamMessage, type StreamEvent } from '../../api/sse';
import {
  EMPTY_CONDITIONS,
  normalizeConditions,
  type Conditions,
  type ConversationSummary,
  type DocumentDetail,
  type DocumentVersion,
  type Turn,
} from '../../api/types';
import { useRefreshUsage } from '../../components/Layout';
import { Button, Chip, ErrorBanner, Loading, Spinner } from '../../components/ui';
import { usd } from '../../lib/format';
import { useSettings } from '../../lib/queries';
import { ConditionEditor } from './ConditionEditor';
import { ConversationList } from './ConversationList';
import { DocumentPreview } from './DocumentPreview';
import { TurnView } from './TurnView';

/** 다른 화면에서 시작된 생성을 다시 읽는 간격 */
const RUNNING_POLL_MS = 3000;

const MODEL_LABEL: Record<string, string> = { 'claude-opus-5-5': 'Opus 5.5', 'claude-sonnet-5-5': 'Sonnet 5.5' };

const ERROR_TEXT: Record<string, string> = {
  BUDGET_EXCEEDED: '이번 달 AI 예산을 모두 썼습니다. 설정에서 사용량을 확인하세요.',
  REFUSED: '모델이 이 요청에 답하지 않았습니다. 표현을 바꿔 다시 요청해 보세요.',
  BUSY: '이 대화에서 이미 답변을 만드는 중입니다. 끝난 뒤 다시 보내 주세요.',
};

const SUBMIT_TOOL = 'submit_document';

const SUGGESTIONS = [
  '선택한 라벨 범위의 자료로 객관식 10문항 단원평가를 만들어줘. 정답률 60% 정도로, 해설 포함.',
  '성적 데이터에서 정답률 30~50%였던 문항들과 비슷한 유형으로 보충 학습지를 만들어줘.',
  '이 단원 핵심 개념을 정리한 수업 자료와 확인 퀴즈 5문항을 만들어줘.',
];

function emptyAssistant(): Turn {
  return {
    id: `live-${Date.now()}`,
    role: 'assistant',
    text: '',
    thinking: null,
    tools: [],
    context: [],
    documentVersions: [],
    conditions: null,
    stopped: false,
    createdAt: new Date().toISOString(),
  };
}

function sameConditions(a: Conditions, b: Conditions) {
  return JSON.stringify(a) === JSON.stringify(b);
}

function upsertVersion(detail: DocumentDetail, v: DocumentVersion): DocumentDetail {
  const versions = detail.versions.some((x) => x.version === v.version)
    ? detail.versions.map((x) => (x.version === v.version ? v : x))
    : [...detail.versions, v].sort((a, b) => a.version - b.version);
  return { ...detail, versions, currentVersion: Math.max(detail.currentVersion, v.version), title: v.payload.title };
}

function normalize(c: ConversationSummary): ConversationSummary {
  return { ...c, conditions: normalizeConditions(c.conditions) };
}

export default function StudioPage() {
  const { conversationId } = useParams<{ conversationId: string }>();
  const navigate = useNavigate();
  const refreshUsage = useRefreshUsage();
  const { data: settings } = useSettings();

  const [conversations, setConversations] = useState<ConversationSummary[]>([]);
  const [archived, setArchived] = useState<ConversationSummary[] | null>(null);
  const [listLoading, setListLoading] = useState(true);
  const [turns, setTurns] = useState<Turn[]>([]);
  const [turnsLoading, setTurnsLoading] = useState(false);
  const [live, setLive] = useState<Turn | null>(null);
  const [streaming, setStreaming] = useState(false);
  const [writingDoc, setWritingDoc] = useState(false);
  const [conditions, setConditions] = useState<Conditions>(EMPTY_CONDITIONS);
  const [doc, setDoc] = useState<DocumentDetail | null>(null);
  const [selectedVersion, setSelectedVersion] = useState<number | null>(null);
  const [composer, setComposer] = useState('');
  const [error, setError] = useState<string | null>(null);
  /** 서버가 답변을 만들고 있는 대화 — 스트림이 끊겨도(새로고침·대화 전환) 생성은 계속된다 */
  const [runningIds, setRunningIds] = useState<string[]>([]);

  const abortRef = useRef<AbortController | null>(null);
  const stopTimer = useRef<ReturnType<typeof setTimeout>>();
  const skipLoad = useRef<string | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  const liveRef = useRef<Turn | null>(null);
  const docRef = useRef<DocumentDetail | null>(null);
  docRef.current = doc;

  const active =
    conversations.find((c) => c.id === conversationId) ?? archived?.find((c) => c.id === conversationId) ?? null;
  const remoteRunning = !!conversationId && !streaming && runningIds.includes(conversationId);

  const refreshRunning = useCallback(async () => {
    try {
      setRunningIds(await api.runningConversations());
    } catch {
      // 상태를 못 읽어도 대화는 그대로 쓸 수 있다
    }
  }, []);

  const loadList = useCallback(async () => {
    try {
      const list = await api.conversations();
      setConversations(list.map(normalize));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setListLoading(false);
    }
  }, []);

  useEffect(() => {
    loadList();
    refreshRunning();
  }, [loadList, refreshRunning]);

  const loadDoc = useCallback(async (documentId: string, select?: number) => {
    const d = await api.document(documentId);
    setDoc(d);
    setSelectedVersion(select ?? d.versions[d.versions.length - 1]?.version ?? null);
  }, []);

  // ── 대화 전환 ──
  useEffect(() => {
    if (conversationId && skipLoad.current === conversationId) {
      skipLoad.current = null;
      return;
    }
    abortRef.current?.abort();
    setLive(null);
    liveRef.current = null;
    setStreaming(false);
    setWritingDoc(false);
    setTurns([]);
    setDoc(null);
    setSelectedVersion(null);
    setError(null);
    if (!conversationId) {
      setConditions(EMPTY_CONDITIONS);
      return;
    }
    let cancelled = false;
    setTurnsLoading(true);
    refreshRunning();
    api
      .messages(conversationId)
      .then((t) => !cancelled && setTurns(t))
      .catch((e) => !cancelled && setError(errorMessage(e)))
      .finally(() => !cancelled && setTurnsLoading(false));
    return () => {
      cancelled = true;
    };
  }, [conversationId]);

  // 다른 화면에서 시작된 생성이 진행 중이면 끝날 때까지 다시 읽는다
  useEffect(() => {
    if (runningIds.length === 0 || streaming) return;
    const timer = setInterval(refreshRunning, RUNNING_POLL_MS);
    return () => clearInterval(timer);
  }, [runningIds.length, streaming, refreshRunning]);

  useEffect(() => {
    if (!remoteRunning || !conversationId) return;
    const id = conversationId;
    const timer = setInterval(() => {
      api
        .messages(id)
        .then(setTurns)
        .catch(() => undefined);
    }, RUNNING_POLL_MS);
    return () => clearInterval(timer);
  }, [remoteRunning, conversationId]);

  // 생성이 끝나면 확정된 대화·산출물을 다시 읽는다
  const wasRemoteRunning = useRef(false);
  useEffect(() => {
    const was = wasRemoteRunning.current;
    wasRemoteRunning.current = remoteRunning;
    if (!was || remoteRunning || !conversationId || streaming) return;
    const id = conversationId;
    Promise.all([api.messages(id), api.conversations()])
      .then(([t, list]) => {
        setTurns(t);
        setConversations(list.map(normalize));
        const documentId = list.find((c) => c.id === id)?.documentId;
        if (documentId) return loadDoc(documentId);
      })
      .catch(() => undefined);
    refreshUsage();
  }, [remoteRunning, conversationId, streaming, loadDoc]);

  // 대화 요약이 준비되면 조건·산출물을 맞춘다
  const activeDocId = active?.documentId ?? null;
  useEffect(() => {
    if (!active) return;
    setConditions(active.conditions ?? EMPTY_CONDITIONS);
  }, [active?.id]);

  useEffect(() => {
    if (!activeDocId || streaming) return;
    if (doc?.id === activeDocId) return;
    loadDoc(activeDocId).catch((e) => setError(errorMessage(e)));
  }, [activeDocId]);

  useEffect(() => () => abortRef.current?.abort(), []);

  useEffect(() => {
    const el = scrollRef.current;
    if (el) el.scrollTop = el.scrollHeight;
  }, [turns.length, live?.text, live?.tools.length]);

  // ── 조건 변경 → 대화에 저장 ──
  const changeConditions = (c: Conditions) => {
    setConditions(c);
    if (conversationId && active && !sameConditions(c, active.conditions)) {
      api
        .updateConversation(conversationId, { conditions: c })
        .then((s) =>
          setConversations((list) => list.map((x) => (x.id === s.id ? { ...s, conditions: normalizeConditions(s.conditions) } : x))),
        )
        .catch((e) => setError(errorMessage(e)));
    }
  };

  // ── 스트리밍 ──
  const patchLive = (fn: (t: Turn) => Turn) => {
    if (!liveRef.current) return;
    liveRef.current = fn(liveRef.current);
    setLive(liveRef.current);
  };

  const handleEvent = (convId: string, e: StreamEvent) => {
    switch (e.event) {
      case 'meta':
        setTurns((ts) => ts.map((t) => (t.id.startsWith('pending-') ? { ...t, id: e.data.userTurnId } : t)));
        break;
      case 'context':
        patchLive((t) => ({ ...t, context: e.data.rules }));
        break;
      case 'thinking':
        patchLive((t) => ({ ...t, thinking: (t.thinking ?? '') + e.data.text }));
        break;
      case 'text':
        patchLive((t) => ({ ...t, text: t.text + e.data.text }));
        break;
      case 'tool': {
        const step = e.data;
        patchLive((t) => ({
          ...t,
          tools: t.tools.some((x) => x.id === step.id) ? t.tools.map((x) => (x.id === step.id ? step : x)) : [...t.tools, step],
        }));
        if (step.name === SUBMIT_TOOL) setWritingDoc(step.status === 'started');
        break;
      }
      case 'document': {
        const v = e.data;
        setWritingDoc(false);
        patchLive((t) => ({ ...t, documentVersions: [...t.documentVersions.filter((x) => x !== v.version), v.version] }));
        if (docRef.current?.id === v.documentId) {
          setDoc((d) => (d && d.id === v.documentId ? upsertVersion(d, v) : d));
          setSelectedVersion(v.version);
        } else {
          loadDoc(v.documentId, v.version).catch((err) => setError(errorMessage(err)));
        }
        setConversations((list) =>
          list.map((c) => (c.id === convId ? { ...c, documentId: v.documentId, currentVersion: v.version } : c)),
        );
        break;
      }
      case 'done':
        setConversations((list) => list.map((c) => (c.id === convId ? { ...c, costUsd: e.data.conversationCostUsd } : c)));
        if (e.data.stopReason === 'stopped') patchLive((t) => ({ ...t, stopped: true }));
        break;
      case 'error':
        setError(ERROR_TEXT[e.data.code] ?? e.data.message);
        if (e.data.code === 'BUSY') refreshRunning();
        break;
    }
  };

  const finishStream = async (convId: string) => {
    const finished = liveRef.current;
    liveRef.current = null;
    setLive(null);
    if (finished && (finished.text || finished.tools.length || finished.thinking)) {
      setTurns((ts) => [...ts, finished]);
    }
    setStreaming(false);
    setWritingDoc(false);
    clearTimeout(stopTimer.current);
    abortRef.current = null;
    refreshUsage();
    refreshRunning();
    try {
      const [t, list] = await Promise.all([api.messages(convId), api.conversations()]);
      setTurns(t);
      setConversations(list.map(normalize));
    } catch {
      // 다시 맞추지 못해도 화면 내용은 그대로 둔다
    }
  };

  const send = async () => {
    const text = composer.trim();
    if (!text || streaming || remoteRunning) return;
    setError(null);

    let convId = conversationId ?? null;
    if (!convId) {
      try {
        const created = await api.createConversation({ conditions });
        convId = created.id;
        setConversations((list) => [{ ...created, conditions: normalizeConditions(created.conditions) }, ...list]);
        skipLoad.current = created.id;
        navigate(`/studio/${created.id}`);
      } catch (e) {
        setError(errorMessage(e));
        return;
      }
    }

    const userTurn: Turn = {
      id: `pending-${Date.now()}`,
      role: 'user',
      text,
      thinking: null,
      tools: [],
      context: [],
      documentVersions: [],
      conditions,
      stopped: false,
      createdAt: new Date().toISOString(),
    };
    setTurns((ts) => [...ts, userTurn]);
    setComposer('');
    liveRef.current = emptyAssistant();
    setLive(liveRef.current);
    setStreaming(true);

    const ctrl = new AbortController();
    abortRef.current = ctrl;
    const id = convId;
    let accepted = false;
    try {
      await streamMessage(
        id,
        { text, conditions },
        (ev) => {
          if (ev.event === 'meta') accepted = true;
          handleEvent(id, ev);
        },
        ctrl.signal,
      );
    } catch (e) {
      // 서버가 받은 뒤 연결만 끊긴 경우 서버는 계속 만들고 있으므로 오류 대신 실행 상태를 따라간다
      const dropped = accepted && !(e instanceof HttpError);
      if (!ctrl.signal.aborted && !dropped) setError(errorMessage(e));
    } finally {
      await finishStream(id);
    }
  };

  const stop = () => {
    if (conversationId && remoteRunning) {
      api
        .stop(conversationId)
        .then(refreshRunning)
        .catch((e) => setError(errorMessage(e)));
      return;
    }
    if (!conversationId || !streaming) return;
    patchLive((t) => ({ ...t, stopped: true }));
    api.stop(conversationId).catch(() => undefined);
    clearTimeout(stopTimer.current);
    stopTimer.current = setTimeout(() => abortRef.current?.abort(), 5000);
  };

  const rename = (id: string, title: string) =>
    api
      .updateConversation(id, { title })
      .then((s) => setConversations((list) => list.map((x) => (x.id === s.id ? { ...x, title: s.title } : x))))
      .catch((e) => setError(errorMessage(e)));

  const archive = (id: string) =>
    api
      .updateConversation(id, { archived: true })
      .then((s) => {
        setConversations((list) => list.filter((x) => x.id !== id));
        setArchived((list) => (list ? [normalize(s), ...list] : list));
        if (id === conversationId) navigate('/studio');
      })
      .catch((e) => setError(errorMessage(e)));

  const loadArchived = () =>
    api
      .conversations(undefined, true)
      .then((list) => setArchived(list.map(normalize)))
      .catch((e) => {
        setArchived([]);
        setError(errorMessage(e));
      });

  const unarchive = (id: string) =>
    api
      .updateConversation(id, { archived: false })
      .then(() => {
        setArchived((list) => list?.filter((x) => x.id !== id) ?? list);
        return loadList();
      })
      .catch((e) => setError(errorMessage(e)));

  const remove = (id: string) =>
    api
      .deleteConversation(id)
      .then(() => {
        setArchived((list) => list?.filter((x) => x.id !== id) ?? list);
        setConversations((list) => list.filter((x) => x.id !== id));
        if (id === conversationId) navigate('/studio');
      })
      .catch((e) => setError(errorMessage(e)));

  const onKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      send();
    }
  };

  const title = active?.title ?? (conversationId ? '대화' : '새 대화');

  return (
    <div className="flex flex-1 min-h-0">
      <ConversationList
        items={conversations}
        activeId={conversationId ?? null}
        runningIds={runningIds}
        onSelect={(id) => navigate(`/studio/${id}`)}
        onNew={() => {
          setConditions(EMPTY_CONDITIONS);
          navigate('/studio');
        }}
        onRename={rename}
        archivedItems={archived}
        onArchive={archive}
        onShowArchived={loadArchived}
        onUnarchive={unarchive}
        onDelete={remove}
        loading={listLoading}
      />

      <div className="flex-1 min-w-0 flex flex-col bg-slate-50">
        <div className="px-5 py-3 border-b border-slate-200 bg-white flex items-center gap-3 min-h-[52px]">
          <div className="font-semibold text-slate-800 truncate">{title}</div>
          {settings && (
            <Chip className="bg-indigo-50 text-indigo-700">
              {MODEL_LABEL[settings.model] ?? settings.model} · {settings.effort}
            </Chip>
          )}
          <div className="ml-auto text-xs text-slate-400 tabular-nums">{active ? `이 대화 ${usd(active.costUsd)}` : ''}</div>
        </div>

        <div ref={scrollRef} className="flex-1 overflow-y-auto px-6 py-5 space-y-5">
          {turnsLoading && <Loading />}
          {!turnsLoading && turns.length === 0 && !live && (
            <div className="max-w-xl mx-auto text-center pt-16 space-y-3">
              <div className="text-lg font-semibold text-slate-700">무엇을 만들까요?</div>
              <div className="text-sm text-slate-500 leading-relaxed">
                아래 조건으로 자료 종류·라벨 범위·문항 유형·목표 정답률을 정하고 자유롭게 요청하세요. AI가 등록한 자료와 성적
                통계를 검색하고, 조건에 맞는 컨텍스트 규칙을 함께 적용해 문제와 교육자료를 만듭니다.
              </div>
              <div className="flex flex-wrap gap-2 justify-center pt-2">
                {SUGGESTIONS.map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => setComposer(s)}
                    className="text-left text-xs text-slate-600 bg-white border border-slate-200 hover:border-indigo-300 rounded-xl px-3 py-2 max-w-[260px]"
                  >
                    {s}
                  </button>
                ))}
              </div>
            </div>
          )}
          {turns.map((t) => (
            <TurnView key={t.id} turn={t} onSelectVersion={setSelectedVersion} />
          ))}
          {live && <TurnView turn={live} live onSelectVersion={setSelectedVersion} />}
          {remoteRunning && (
            <div className="flex items-center gap-2 text-xs text-slate-500 bg-white border border-slate-200 rounded-xl px-3 py-2 w-fit">
              <Spinner className="text-indigo-500" />
              <span>답변을 만드는 중입니다. 끝나면 이 화면에 자동으로 나타납니다.</span>
            </div>
          )}
        </div>

        <div className="border-t border-slate-200 bg-white p-3 space-y-2">
          <ErrorBanner message={error} />
          <ConditionEditor value={conditions} onChange={changeConditions} requestText={composer} />
          <div className="flex gap-2 items-end">
            <textarea
              value={composer}
              onChange={(e) => setComposer(e.target.value)}
              onKeyDown={onKeyDown}
              rows={2}
              aria-label="요청 입력"
              placeholder={
                streaming || remoteRunning
                  ? '답변을 만드는 중입니다… (끝나면 다음 요청을 보낼 수 있습니다)'
                  : '요청이나 수정 사항을 입력하세요 (Shift+Enter 줄바꿈)'
              }
              className="flex-1 border border-slate-300 rounded-xl px-3 py-2 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-indigo-200 focus:border-indigo-400"
            />
            {streaming || remoteRunning ? (
              <Button variant="secondary" className="py-2" onClick={stop}>
                <Spinner className="text-indigo-500" />
                생성 중 · 중단
              </Button>
            ) : (
              <Button variant="primary" className="py-2" onClick={send} disabled={!composer.trim()}>
                보내기
              </Button>
            )}
          </div>
        </div>
      </div>

      <DocumentPreview
        doc={doc}
        selectedVersion={selectedVersion}
        onSelectVersion={setSelectedVersion}
        writing={writingDoc}
        similarityWarn={settings?.similarityWarn ?? 0.92}
      />
    </div>
  );
}
