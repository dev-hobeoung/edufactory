import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useRef } from 'react';
import { useParams, useSearchParams } from 'react-router-dom';
import { api, errorMessage } from '../api/client';
import { Button, ErrorBanner, Loading } from '../components/ui';
import { A4Document, DOC_MODE_LABEL, normalizePayload, type DocMode } from '../documents/DocumentView';

const MODES: DocMode[] = ['student', 'answer'];

/** 인쇄 화면 — 문제지·정답해설지를 A4 로 그리고 브라우저 인쇄(PDF 저장)를 연다. ?doc=student|answer|all */
export default function PrintDocumentPage() {
  const { id = '' } = useParams<{ id: string }>();
  const [params] = useSearchParams();
  const versionParam = Number(params.get('version'));
  const docParam = params.get('doc');
  const modes: DocMode[] = MODES.includes(docParam as DocMode) ? [docParam as DocMode] : MODES;
  const query = useQuery({ queryKey: ['document', id], queryFn: () => api.document(id) });
  const printed = useRef(false);

  const version =
    query.data?.versions.find((v) => v.version === versionParam) ?? query.data?.versions[query.data.versions.length - 1] ?? null;
  const payload = useMemo(() => (version ? normalizePayload(version.payload) : null), [version]);

  useEffect(() => {
    if (!version || !payload || printed.current) return;
    printed.current = true;
    document.title = `${payload.title} v${version.version} ${modes.length === 1 ? DOC_MODE_LABEL[modes[0]] : ''}`.trim();
    // KaTeX 글꼴이 실릴 때까지 기다린 뒤 인쇄
    const go = () => setTimeout(() => window.print(), 400);
    if (document.fonts?.ready) document.fonts.ready.then(go);
    else go();
  }, [version, payload, modes]);

  if (query.isLoading) return <Loading />;
  if (!version || !payload)
    return (
      <div className="p-6">
        <ErrorBanner message={query.error ? errorMessage(query.error) : '산출물을 찾을 수 없습니다.'} onRetry={() => query.refetch()} />
      </div>
    );

  return (
    <div className="min-h-full bg-slate-100 py-6 print:bg-white print:py-0">
      <div className="no-print max-w-[210mm] mx-auto mb-4 flex justify-between items-center px-2 gap-3">
        <div className="text-sm text-slate-500">
          인쇄 창에서 "PDF로 저장"을 고르면 PDF가 만들어집니다.
          {modes.length === 1 ? ` (${DOC_MODE_LABEL[modes[0]]})` : ' (문제지 · 정답·해설)'}
        </div>
        <Button variant="primary" onClick={() => window.print()}>
          인쇄 / PDF 저장
        </Button>
      </div>
      <div className="space-y-6 print:space-y-0">
        {modes.map((m) => (
          <A4Document key={m} mode={m} payload={payload} version={version.version} />
        ))}
      </div>
    </div>
  );
}
