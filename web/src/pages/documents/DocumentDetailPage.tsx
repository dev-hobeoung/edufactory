import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, errorMessage } from '../../api/client';
import type { DocPayload, DocumentStatus } from '../../api/types';
import { Page } from '../../components/Layout';
import { Button, Card, Chip, ConfirmButton, ErrorBanner, Loading, Segmented, inputClass } from '../../components/ui';
import { DOC_MODE_LABEL, EvidenceList, normalizePayload, ScreenDocument, type DocMode } from '../../documents/DocumentView';
import { dateTime } from '../../lib/format';
import { DOC_STATUS_LABEL, DOC_STATUS_TONE } from '../../lib/labels';
import { useSettings } from '../../lib/queries';
import { useToast } from '../../lib/toast';
import { PayloadEditor, payloadProblems } from './PayloadEditor';

type View = DocMode | 'edit' | 'evidence';

export default function DocumentDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const toast = useToast();
  const { data: settings } = useSettings();
  const query = useQuery({ queryKey: ['document', id], queryFn: () => api.document(id) });
  const doc = query.data;
  const [selected, setSelected] = useState<number | null>(null);
  const [view, setView] = useState<View>('student');
  const [draft, setDraft] = useState<DocPayload | null>(null);
  const [error, setError] = useState<string | null>(null);

  const version = doc?.versions.find((v) => v.version === selected) ?? doc?.versions[doc.versions.length - 1] ?? null;
  const isLatest = !!doc && !!version && version.version === doc.currentVersion;

  useEffect(() => {
    if (doc && selected === null) setSelected(doc.currentVersion);
  }, [doc, selected]);

  // 편집 화면에 들어가면 보고 있는 버전으로 시작
  useEffect(() => {
    if (view === 'edit' && version && !draft) setDraft(normalizePayload(structuredClone(version.payload)));
  }, [view, version, draft]);

  const dirty = useMemo(
    () => !!draft && !!version && JSON.stringify(draft) !== JSON.stringify(normalizePayload(version.payload)),
    [draft, version],
  );
  const problems = draft ? payloadProblems(draft) : [];

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['document', id] });
    qc.invalidateQueries({ queryKey: ['documents'] });
  };
  const saveVersion = useMutation({
    mutationFn: (p: DocPayload) => api.saveVersion(id, p),
    onSuccess: (v) => {
      toast(`v${v.version} 으로 저장했습니다`);
      setDraft(null);
      setSelected(v.version);
      setView('student');
      setError(null);
      invalidate();
    },
    onError: (e) => setError(errorMessage(e)),
  });
  const restore = useMutation({
    mutationFn: (v: number) => api.restoreVersion(id, v),
    onSuccess: (v) => {
      toast(`v${v.version} 으로 되돌렸습니다`);
      setSelected(v.version);
      invalidate();
    },
    onError: (e) => setError(errorMessage(e)),
  });
  const patch = useMutation({
    mutationFn: (body: { title?: string; status?: DocumentStatus }) => api.updateDocument(id, body),
    onSuccess: invalidate,
    onError: (e) => setError(errorMessage(e)),
  });
  const remove = useMutation({
    mutationFn: () => api.deleteDocument(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['documents'] });
      navigate('/documents');
    },
    onError: (e) => setError(errorMessage(e)),
  });

  if (query.isLoading) return <Loading />;
  if (!doc || !version)
    return (
      <Page>
        <ErrorBanner message={query.error ? errorMessage(query.error) : '산출물을 찾을 수 없습니다'} onRetry={() => query.refetch()} />
      </Page>
    );

  const exportMd = (answers: boolean) =>
    api
      .exportDocument(doc.id, version.version, answers, `${doc.title}-v${version.version}${answers ? '-정답' : ''}.md`)
      .catch((e) => setError(errorMessage(e)));

  return (
    <Page wide>
      <div className="flex items-start gap-4 flex-wrap">
        <div className="flex-1 min-w-0">
          <Link to="/documents" className="text-xs text-slate-400 hover:text-slate-600">
            ← 산출물 목록
          </Link>
          <h1 className="text-2xl font-bold text-slate-800 mt-0.5">{doc.title}</h1>
          <div className="flex items-center gap-2 flex-wrap mt-1 text-xs text-slate-500">
            <Chip className={DOC_STATUS_TONE[doc.status]}>{DOC_STATUS_LABEL[doc.status]}</Chip>
            <span>
              문항 {doc.itemCount}
              {doc.totalScore !== null ? ` · 총점 ${doc.totalScore}` : ''}
            </span>
            {doc.conversationId && (
              <Link to={`/studio/${doc.conversationId}`} className="text-indigo-600 hover:underline">
                대화로 이어서 고치기
              </Link>
            )}
          </div>
        </div>
        <div className="flex gap-2 flex-wrap items-center">
          <select
            value={doc.status}
            onChange={(e) => patch.mutate({ status: e.target.value as DocumentStatus })}
            aria-label="상태"
            className={`${inputClass} text-xs`}
          >
            {(Object.keys(DOC_STATUS_LABEL) as DocumentStatus[]).map((s) => (
              <option key={s} value={s}>
                {DOC_STATUS_LABEL[s]}
              </option>
            ))}
          </select>
          <a href={`/print/documents/${doc.id}?version=${version.version}&doc=all`} target="_blank" rel="noreferrer">
            <Button>인쇄 / PDF</Button>
          </a>
          <Button onClick={() => exportMd(false)}>MD (문제)</Button>
          <Button onClick={() => exportMd(true)}>MD (정답 포함)</Button>
          <ConfirmButton label="삭제" variant="secondary" size="sm" onConfirm={() => remove.mutate()} />
        </div>
      </div>
      <ErrorBanner message={error} />

      <div className="flex items-center gap-2 flex-wrap">
        <select
          value={version.version}
          onChange={(e) => {
            setSelected(Number(e.target.value));
            setDraft(null);
          }}
          aria-label="버전"
          className={`${inputClass} text-xs`}
          disabled={dirty}
        >
          {[...doc.versions].reverse().map((v) => (
            <option key={v.version} value={v.version}>
              v{v.version} · {dateTime(v.createdAt)}
              {v.version === doc.currentVersion ? ' (현재)' : ''}
            </option>
          ))}
        </select>
        {!isLatest && (
          <Button size="xs" onClick={() => restore.mutate(version.version)} disabled={restore.isPending}>
            이 버전으로 되돌리기
          </Button>
        )}
        {version.payload.changeNote && <span className="text-xs text-slate-500">변경: {version.payload.changeNote}</span>}
        <div className="ml-auto">
          <Segmented<View>
            label="보기"
            value={view}
            onChange={(v) => setView(v)}
            options={[
              { value: 'student', label: DOC_MODE_LABEL.student },
              { value: 'answer', label: DOC_MODE_LABEL.answer },
              { value: 'evidence', label: '근거' },
              { value: 'edit', label: dirty ? '편집 중 •' : '편집' },
            ]}
          />
        </div>
      </div>

      {version.warnings.length > 0 && view !== 'edit' && (
        <ul className="space-y-1">
          {version.warnings.map((w, i) => (
            <li key={i} className="text-xs text-amber-800 bg-amber-50 border border-amber-100 rounded-md px-2 py-1">
              {w}
            </li>
          ))}
        </ul>
      )}

      {view === 'edit' && draft ? (
        <div className="grid xl:grid-cols-2 gap-4 items-start">
          <Card className="p-4">
            <PayloadEditor value={draft} onChange={setDraft} />
          </Card>
          <div className="space-y-3 xl:sticky xl:top-0">
            <Card className="p-3 space-y-2">
              {problems.length > 0 && (
                <ul className="text-xs text-red-700 space-y-0.5">
                  {problems.map((p, i) => (
                    <li key={i}>· {p}</li>
                  ))}
                </ul>
              )}
              <div className="flex gap-2 justify-end">
                <Button
                  disabled={!dirty}
                  onClick={() => setDraft(normalizePayload(structuredClone(version.payload)))}
                >
                  되돌리기
                </Button>
                <Button
                  variant="primary"
                  disabled={!dirty || problems.length > 0 || saveVersion.isPending}
                  onClick={() => saveVersion.mutate(draft)}
                >
                  {saveVersion.isPending ? '검증·저장 중…' : '새 버전으로 저장'}
                </Button>
              </div>
              <p className="text-[11px] text-slate-400">
                저장하면 서버가 검증·자료 유사도·예상 정답률을 다시 계산해 새 버전을 만듭니다. AI 는 다음 요청에서 이 버전을 이어 씁니다.
              </p>
            </Card>
            <Card className="p-4 max-h-[75vh] overflow-y-auto">
              <ScreenDocument
                version={{ ...version, payload: draft, prediction: [], similar: [], warnings: [] }}
                mode="answer"
                similarityWarn={settings?.similarityWarn ?? 0.92}
              />
            </Card>
          </div>
        </div>
      ) : view === 'evidence' ? (
        <Card className="p-4 space-y-4">
          {version.payload.summary && (
            <div>
              <div className="text-xs font-semibold text-slate-500 mb-1">개요·의도</div>
              <div className="text-sm whitespace-pre-wrap">{version.payload.summary}</div>
            </div>
          )}
          <div>
            <div className="text-xs font-semibold text-slate-500 mb-1">근거</div>
            <EvidenceList payload={version.payload} />
          </div>
          <div>
            <div className="text-xs font-semibold text-slate-500 mb-1">문항별 예측·유사 자료</div>
            {version.prediction.length === 0 && version.similar.length === 0 ? (
              <div className="text-xs text-slate-400">계산된 값이 없습니다.</div>
            ) : (
              <table className="text-xs w-full">
                <thead className="text-slate-500">
                  <tr>
                    <th className="text-left py-1">문항</th>
                    <th className="text-left py-1">예측 정답률</th>
                    <th className="text-left py-1">근거 이웃</th>
                    <th className="text-left py-1">가장 비슷한 자료</th>
                  </tr>
                </thead>
                <tbody>
                  {version.prediction.map((p) => {
                    const sim = [...version.similar].filter((s) => s.itemNumber === p.itemNumber).sort((a, b) => b.similarity - a.similarity)[0];
                    return (
                      <tr key={p.itemNumber} className="border-t border-slate-100 align-top">
                        <td className="py-1 font-semibold">{p.itemNumber}</td>
                        <td className="py-1">
                          {p.expectedRate === null ? '–' : `${Math.round(p.expectedRate * 100)}%`} ({p.confidence})
                        </td>
                        <td className="py-1 text-slate-500">
                          {p.neighbors.slice(0, 3).map((n) => (
                            <div key={n.chunkId}>
                              {n.materialTitle} {n.position ?? ''} · 유사 {n.similarity.toFixed(2)} · {Math.round(n.avgRate * 100)}% (n={n.n})
                            </div>
                          ))}
                        </td>
                        <td className="py-1 text-slate-500">
                          {sim ? (
                            <Link to={`/materials/${sim.materialId}`} className="text-indigo-600 hover:underline">
                              {sim.materialTitle} {sim.position ?? ''} ({sim.similarity.toFixed(2)})
                            </Link>
                          ) : (
                            '–'
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>
        </Card>
      ) : (
        <Card className="p-5 max-w-4xl">
          <ScreenDocument version={version} mode={view === 'answer' ? 'answer' : 'student'} similarityWarn={settings?.similarityWarn ?? 0.92} />
        </Card>
      )}
    </Page>
  );
}
