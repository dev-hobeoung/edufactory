import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, errorMessage } from '../../api/client';
import type { ChunkType, SearchHit } from '../../api/types';
import { LabelList } from '../../components/LabelBadge';
import { LabelPicker, MaterialPicker, MultiToggle } from '../../components/pickers';
import { StatBadges } from '../../components/StatBadges';
import { Button, Card, Chip, Empty, ErrorBanner, Loading, inputClass } from '../../components/ui';
import { CHUNK_TYPES, CHUNK_TYPE_LABEL, CHUNK_TYPE_TONE } from '../../lib/labels';
import { RichText } from '../../lib/math';

/** 검색 테스트 — AI 가 쓰는 의미 검색을 직접 해 본다 */
export function SearchPanel() {
  const [query, setQuery] = useState('');
  const [labelIds, setLabelIds] = useState<string[]>([]);
  const [materialIds, setMaterialIds] = useState<string[]>([]);
  const [chunkTypes, setChunkTypes] = useState<ChunkType[]>([]);
  const [examOnly, setExamOnly] = useState(false);
  const [limit, setLimit] = useState(10);
  const [hits, setHits] = useState<SearchHit[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async () => {
    if (!query.trim()) return;
    setLoading(true);
    setError(null);
    try {
      setHits(await api.search({ query: query.trim(), labelIds, materialIds, chunkTypes, examOnly, limit }));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card className="p-4 space-y-3">
      <div className="font-semibold text-slate-700 text-sm">검색 테스트</div>
      <div className="flex gap-2">
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && !e.nativeEvent.isComposing && run()}
          placeholder="예: 이차함수의 최댓값을 구하는 문항"
          aria-label="검색어"
          className={`${inputClass} flex-1`}
        />
        <input
          type="number"
          min={1}
          max={50}
          value={limit}
          onChange={(e) => setLimit(Math.max(1, Math.min(50, Number(e.target.value) || 10)))}
          aria-label="결과 수"
          className={`${inputClass} w-20`}
        />
        <Button variant="primary" onClick={run} disabled={!query.trim() || loading}>
          검색
        </Button>
      </div>
      <div className="grid grid-cols-2 gap-2">
        <LabelPicker value={labelIds} onChange={setLabelIds} placeholder="라벨 범위 (선택)" />
        <MaterialPicker value={materialIds} onChange={setMaterialIds} placeholder="자료 범위 (선택)" />
      </div>
      <div className="flex items-center gap-3 flex-wrap">
        <MultiToggle
          label="청크 종류"
          value={chunkTypes}
          options={CHUNK_TYPES.map((t) => ({ value: t, label: CHUNK_TYPE_LABEL[t] }))}
          onChange={setChunkTypes}
        />
        <label className="text-xs text-slate-600 flex items-center gap-1">
          <input type="checkbox" checked={examOnly} onChange={(e) => setExamOnly(e.target.checked)} />
          시험 자료만
        </label>
      </div>
      <ErrorBanner message={error} />
      {loading && <Loading label="검색 중…" />}
      {!loading && hits && hits.length === 0 && <Empty>결과가 없습니다.</Empty>}
      {!loading && hits && hits.length > 0 && (
        <ol className="space-y-2">
          {hits.map((h) => (
            <li key={h.chunk.id} className="border border-slate-200 rounded-lg p-3 space-y-1.5">
              <div className="flex items-center gap-2 flex-wrap text-xs">
                <span className="font-semibold tabular-nums text-indigo-700">{h.similarity.toFixed(3)}</span>
                <Chip className={CHUNK_TYPE_TONE[h.chunk.chunkType]}>{CHUNK_TYPE_LABEL[h.chunk.chunkType]}</Chip>
                <Link to={`/materials/${h.materialId}`} className="text-slate-700 hover:text-indigo-700 font-medium">
                  {h.materialTitle}
                </Link>
                {(h.chunk.position || h.chunk.itemNo) && <span className="text-slate-400">{h.chunk.position ?? `${h.chunk.itemNo}번`}</span>}
                <LabelList labels={[...h.materialLabels, ...h.chunk.labels]} max={5} />
                <StatBadges stat={h.chunk.stat} />
              </div>
              <RichText text={h.chunk.content.length > 600 ? `${h.chunk.content.slice(0, 600)}…` : h.chunk.content} className="text-sm text-slate-700" />
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}
