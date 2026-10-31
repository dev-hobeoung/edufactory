import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, errorMessage } from '../../api/client';
import type { DocumentStatus, OutputKind } from '../../api/types';
import { Page } from '../../components/Layout';
import { Button, Chip, Empty, ErrorBanner, Loading, PageHeader, inputClass } from '../../components/ui';
import { relTime, SIM_TONE_CLASS, similarityTone } from '../../lib/format';
import { useDebounced } from '../../lib/hooks';
import { DOC_STATUS_LABEL, DOC_STATUS_TONE, OUTPUT_KINDS, OUTPUT_KIND_LABEL } from '../../lib/labels';
import { useSettings } from '../../lib/queries';

export default function DocumentsPage() {
  const navigate = useNavigate();
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  // 대화 없이 빈 산출물(v1)을 만들고 편집 화면으로 간다
  const createBlank = async () => {
    setCreating(true);
    setCreateError(null);
    try {
      const v = await api.createDocument({
        title: '새 자료',
        kind: 'WORKSHEET',
        audience: null,
        summary: null,
        instructions: null,
        sections: [{ title: null, body: '여기에 내용을 작성하세요.', items: [] }],
        evidence: [],
        changeNote: null,
      });
      navigate(`/documents/${v.documentId}`);
    } catch (e) {
      setCreateError(errorMessage(e));
    } finally {
      setCreating(false);
    }
  };
  const { data: settings } = useSettings();
  const [q, setQ] = useState('');
  const [kind, setKind] = useState<OutputKind | ''>('');
  const [status, setStatus] = useState<DocumentStatus | ''>('');
  const dq = useDebounced(q);
  const query = useQuery({ queryKey: ['documents', { dq, kind, status }], queryFn: () => api.documents({ q: dq, kind, status }) });
  const list = query.data ?? [];
  const warn = settings?.similarityWarn ?? 0.92;

  return (
    <Page wide>
      <PageHeader
        title="산출물"
        sub="작업실에서 만든 문제지·학습지·수업 자료입니다. 버전별로 고치고 인쇄·내보내기 할 수 있습니다."
        actions={
          <Button variant="primary" disabled={creating} onClick={createBlank}>
            새로 직접 작성
          </Button>
        }
      />
      {createError && <ErrorBanner message={createError} />}
      <div className="flex gap-2 flex-wrap">
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="제목 검색" aria-label="제목 검색" className={`${inputClass} w-64`} />
        <select value={kind} onChange={(e) => setKind(e.target.value as OutputKind | '')} className={inputClass} aria-label="종류">
          <option value="">모든 종류</option>
          {OUTPUT_KINDS.map((k) => (
            <option key={k} value={k}>
              {OUTPUT_KIND_LABEL[k]}
            </option>
          ))}
        </select>
        <select value={status} onChange={(e) => setStatus(e.target.value as DocumentStatus | '')} className={inputClass} aria-label="상태">
          <option value="">모든 상태</option>
          {(Object.keys(DOC_STATUS_LABEL) as DocumentStatus[]).map((s) => (
            <option key={s} value={s}>
              {DOC_STATUS_LABEL[s]}
            </option>
          ))}
        </select>
      </div>
      <ErrorBanner message={query.error ? errorMessage(query.error) : null} onRetry={() => query.refetch()} />
      {query.isLoading ? (
        <Loading />
      ) : list.length === 0 ? (
        <Empty>
          산출물이 없습니다. <Link to="/studio" className="text-indigo-600 hover:underline">작업실</Link>에서 만들어 보세요.
        </Empty>
      ) : (
        <div className="bg-white border border-slate-200 rounded-lg overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-xs text-slate-500">
              <tr>
                <th className="text-left px-3 py-2 font-medium">제목</th>
                <th className="text-left px-3 py-2 font-medium">종류</th>
                <th className="text-left px-3 py-2 font-medium">상태</th>
                <th className="text-right px-3 py-2 font-medium">문항</th>
                <th className="text-right px-3 py-2 font-medium">총점</th>
                <th className="text-right px-3 py-2 font-medium">최대 유사도</th>
                <th className="text-right px-3 py-2 font-medium">버전</th>
                <th className="text-right px-3 py-2 font-medium">수정</th>
              </tr>
            </thead>
            <tbody>
              {list.map((d) => (
                <tr key={d.id} className="border-t border-slate-100 hover:bg-slate-50 cursor-pointer" onClick={() => navigate(`/documents/${d.id}`)}>
                  <td className="px-3 py-2 font-medium text-slate-800">{d.title}</td>
                  <td className="px-3 py-2 text-slate-600">{OUTPUT_KIND_LABEL[d.kind] ?? d.kind}</td>
                  <td className="px-3 py-2">
                    <Chip className={DOC_STATUS_TONE[d.status]}>{DOC_STATUS_LABEL[d.status]}</Chip>
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums">{d.itemCount}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{d.totalScore ?? '–'}</td>
                  <td className="px-3 py-2 text-right">
                    {d.maxSimilarity === null ? (
                      <span className="text-slate-300">–</span>
                    ) : (
                      <Chip className={SIM_TONE_CLASS[similarityTone(d.maxSimilarity, warn)]}>{d.maxSimilarity.toFixed(2)}</Chip>
                    )}
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums text-slate-500">v{d.currentVersion}</td>
                  <td className="px-3 py-2 text-right text-xs text-slate-500 whitespace-nowrap">{relTime(d.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Page>
  );
}
