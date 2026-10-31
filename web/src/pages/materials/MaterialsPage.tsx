import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { errorMessage, api } from '../../api/client';
import type { MaterialKind, MaterialStatus } from '../../api/types';
import { LabelList } from '../../components/LabelBadge';
import { Page } from '../../components/Layout';
import { LabelPicker } from '../../components/pickers';
import { Button, Chip, Empty, ErrorBanner, Loading, PageHeader, inputClass } from '../../components/ui';
import { bytes, relTime } from '../../lib/format';
import { useDebounced } from '../../lib/hooks';
import {
  MATERIAL_KINDS,
  MATERIAL_KIND_LABEL,
  MATERIAL_STATUSES,
  MATERIAL_STATUS_LABEL,
  PROCESSING_STATUSES,
  STATUS_TONE,
} from '../../lib/labels';
import { SearchPanel } from './SearchPanel';
import { UploadModal } from './UploadModal';

export function StatusBadge({ status, message }: { status: MaterialStatus; message?: string | null }) {
  const busy = PROCESSING_STATUSES.includes(status);
  return (
    <Chip className={STATUS_TONE[status]} title={message ?? undefined}>
      {busy && <span className="w-1.5 h-1.5 rounded-full bg-current animate-pulse" aria-hidden />}
      {MATERIAL_STATUS_LABEL[status]}
    </Chip>
  );
}

export default function MaterialsPage() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [q, setQ] = useState('');
  const [labelIds, setLabelIds] = useState<string[]>([]);
  const [kind, setKind] = useState<MaterialKind | ''>('');
  const [examOnly, setExamOnly] = useState(false);
  const [status, setStatus] = useState<MaterialStatus | ''>('');
  const [uploadOpen, setUploadOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const dq = useDebounced(q);

  const query = useQuery({
    queryKey: ['materials', { dq, labelIds, kind, examOnly, status }],
    queryFn: () => api.materials({ q: dq, labelIds, kind, exam: examOnly, status }),
    refetchInterval: (qr) => (qr.state.data?.some((m) => PROCESSING_STATUSES.includes(m.status)) ? 2500 : false),
  });
  const list = query.data ?? [];

  return (
    <Page wide>
      <PageHeader
        title="자료"
        sub="교재·시험지·학습지를 올리면 AI가 문항·지문·개념 단위로 구조화하고 임베딩합니다. 라벨로 범위를 나눠 검색과 규칙에 씁니다."
        actions={
          <>
            <Button onClick={() => setSearchOpen((v) => !v)}>{searchOpen ? '검색 테스트 닫기' : '검색 테스트'}</Button>
            <Button variant="primary" onClick={() => setUploadOpen(true)}>
              + 자료 올리기
            </Button>
          </>
        }
      />
      {searchOpen && <SearchPanel />}
      <div className="flex flex-wrap gap-2 items-center">
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="제목 검색"
          aria-label="제목 검색"
          className={`${inputClass} w-56`}
        />
        <div className="w-72">
          <LabelPicker value={labelIds} onChange={setLabelIds} placeholder="라벨 필터 (모두 포함)" />
        </div>
        <select value={kind} onChange={(e) => setKind(e.target.value as MaterialKind | '')} className={inputClass} aria-label="종류">
          <option value="">모든 종류</option>
          {MATERIAL_KINDS.map((k) => (
            <option key={k} value={k}>
              {MATERIAL_KIND_LABEL[k]}
            </option>
          ))}
        </select>
        <select value={status} onChange={(e) => setStatus(e.target.value as MaterialStatus | '')} className={inputClass} aria-label="상태">
          <option value="">모든 상태</option>
          {MATERIAL_STATUSES.map((s) => (
            <option key={s} value={s}>
              {MATERIAL_STATUS_LABEL[s]}
            </option>
          ))}
        </select>
        <label className="text-sm text-slate-600 flex items-center gap-1.5">
          <input type="checkbox" checked={examOnly} onChange={(e) => setExamOnly(e.target.checked)} />
          시험 자료만
        </label>
      </div>
      <ErrorBanner message={query.error ? errorMessage(query.error) : null} onRetry={() => query.refetch()} />
      {query.isLoading ? (
        <Loading />
      ) : list.length === 0 ? (
        <Empty>자료가 없습니다. 오른쪽 위 "자료 올리기"로 시작하세요.</Empty>
      ) : (
        <div className="bg-white border border-slate-200 rounded-lg overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-xs text-slate-500">
              <tr>
                <th className="text-left px-3 py-2 font-medium">제목</th>
                <th className="text-left px-3 py-2 font-medium">종류</th>
                <th className="text-left px-3 py-2 font-medium">라벨</th>
                <th className="text-left px-3 py-2 font-medium">상태</th>
                <th className="text-right px-3 py-2 font-medium">청크</th>
                <th className="text-right px-3 py-2 font-medium">파일</th>
                <th className="text-right px-3 py-2 font-medium">수정</th>
              </tr>
            </thead>
            <tbody>
              {list.map((m) => (
                <tr
                  key={m.id}
                  className="border-t border-slate-100 hover:bg-slate-50 cursor-pointer"
                  onClick={() => navigate(`/materials/${m.id}`)}
                >
                  <td className="px-3 py-2">
                    <Link to={`/materials/${m.id}`} className="font-medium text-slate-800 hover:text-indigo-700" onClick={(e) => e.stopPropagation()}>
                      {m.title}
                    </Link>
                    <div className="flex gap-1 mt-0.5">
                      {m.isExam && <Chip className="bg-indigo-50 text-indigo-700">시험</Chip>}
                      {m.scoreSetCount > 0 && <Chip className="bg-emerald-50 text-emerald-700">성적 {m.scoreSetCount}세트</Chip>}
                      {m.structuredBy === 'RULE' && <Chip className="bg-amber-50 text-amber-700" title="AI 키가 없어 규칙으로 나눴습니다">규칙 분할</Chip>}
                    </div>
                  </td>
                  <td className="px-3 py-2 text-slate-600 whitespace-nowrap">{MATERIAL_KIND_LABEL[m.kind]}</td>
                  <td className="px-3 py-2">
                    <LabelList labels={m.labels} max={4} />
                  </td>
                  <td className="px-3 py-2">
                    <StatusBadge status={m.status} message={m.error ?? m.statusMessage} />
                    {m.statusMessage && PROCESSING_STATUSES.includes(m.status) && (
                      <div className="text-[11px] text-slate-400 mt-0.5">{m.statusMessage}</div>
                    )}
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums text-slate-600">
                    {m.chunkCount}
                    {m.chunkCount > 0 && m.embeddedCount < m.chunkCount && (
                      <div className="text-[11px] text-slate-400">임베딩 {m.embeddedCount}</div>
                    )}
                  </td>
                  <td className="px-3 py-2 text-right text-xs text-slate-500 whitespace-nowrap">
                    {m.fileName ? (
                      <>
                        <div className="truncate max-w-[160px]" title={m.fileName}>
                          {m.fileName}
                        </div>
                        <div>{bytes(m.fileSize)}</div>
                      </>
                    ) : (
                      '붙여넣기'
                    )}
                  </td>
                  <td className="px-3 py-2 text-right text-xs text-slate-500 whitespace-nowrap">{relTime(m.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <UploadModal
        open={uploadOpen}
        onClose={() => setUploadOpen(false)}
        onUploaded={(m) => {
          setUploadOpen(false);
          qc.invalidateQueries({ queryKey: ['materials'] });
          navigate(`/materials/${m.id}`);
        }}
      />
    </Page>
  );
}
