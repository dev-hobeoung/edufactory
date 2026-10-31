import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { useEffect, useMemo, useState } from 'react';
import { api, errorMessage } from '../../api/client';
import type { ExamItem, ScoreEntries, ScoreMode, ScoreSet } from '../../api/types';
import { Button, ErrorBanner, Loading, Segmented } from '../../components/ui';
import { useToast } from '../../lib/toast';
import { ExamStatView } from './ExamStatView';
import { ImportModal } from './ImportModal';
import { ItemStatsTable } from './ItemStatsTable';

type Row = { studentKey: string; values: Record<string, string> };

function rowsFrom(entries: ScoreEntries, mode: ScoreMode): Row[] {
  return entries.rows.map((r) => {
    const values: Record<string, string> = {};
    for (const item of entries.items) {
      const v = r.values[item.chunkId];
      if (!v) continue;
      const shown = mode === 'RESPONSE' ? (v.response ?? (v.score !== null ? String(v.score) : null)) : v.score !== null ? String(v.score) : null;
      if (shown !== null) values[item.chunkId] = shown;
    }
    return { studentKey: r.studentKey, values };
  });
}

/** 성적 세트 하나의 성적 표 — 학생 × 문항. 엑셀에서 복사한 범위를 칸에 붙여넣을 수 있다. */
export function ScoreGrid({ set, items, onChanged }: { set: ScoreSet; items: ExamItem[]; onChanged: () => void }) {
  const qc = useQueryClient();
  const toast = useToast();
  const entriesQuery = useQuery({ queryKey: ['score-entries', set.id], queryFn: () => api.scoreEntries(set.id) });
  const statsQuery = useQuery({ queryKey: ['score-stats', set.id], queryFn: () => api.scoreSetStats(set.id) });
  const [mode, setMode] = useState<ScoreMode>('SCORE');
  const [rows, setRows] = useState<Row[]>([]);
  const [dirty, setDirty] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [importOpen, setImportOpen] = useState(false);
  const [view, setView] = useState<'grid' | 'stats'>('grid');

  const columns = entriesQuery.data?.items ?? items;

  useEffect(() => {
    const e = entriesQuery.data;
    if (!e) return;
    const hasResponse = e.rows.some((r) => Object.values(r.values).some((v) => v.response !== null));
    const m: ScoreMode = hasResponse ? 'RESPONSE' : 'SCORE';
    setMode(m);
    setRows(rowsFrom(e, m));
    setDirty(false);
  }, [entriesQuery.data]);

  const switchMode = (m: ScoreMode) => {
    setMode(m);
    if (!dirty && entriesQuery.data) setRows(rowsFrom(entriesQuery.data, m));
  };

  const setCell = (ri: number, chunkId: string, v: string) => {
    setRows((rs) => rs.map((r, i) => (i === ri ? { ...r, values: { ...r.values, [chunkId]: v } } : r)));
    setDirty(true);
  };
  const setKey = (ri: number, v: string) => {
    setRows((rs) => rs.map((r, i) => (i === ri ? { ...r, studentKey: v } : r)));
    setDirty(true);
  };

  /** 탭·줄바꿈으로 나뉜 붙여넣기를 (ri, ci) 부터 채운다. ci=-1 은 학생 키 열 */
  const paste = (ri: number, ci: number, text: string): boolean => {
    if (!/[\t\n]/.test(text.trim())) return false;
    const lines = text.replace(/\r/g, '').split('\n');
    if (lines[lines.length - 1] === '') lines.pop();
    setRows((rs) => {
      const next = rs.map((r) => ({ ...r, values: { ...r.values } }));
      lines.forEach((line, li) => {
        const cells = line.split('\t');
        const target = ri + li;
        while (next.length <= target) next.push({ studentKey: '', values: {} });
        cells.forEach((cell, k) => {
          const col = ci + k;
          if (col === -1) next[target].studentKey = cell.trim();
          else if (col >= 0 && col < columns.length) next[target].values[columns[col].chunkId] = cell.trim();
        });
      });
      return next;
    });
    setDirty(true);
    return true;
  };

  const duplicateKeys = useMemo(() => {
    const seen = new Set<string>();
    const dup = new Set<string>();
    for (const r of rows) {
      const k = r.studentKey.trim();
      if (!k) continue;
      if (seen.has(k)) dup.add(k);
      seen.add(k);
    }
    return dup;
  }, [rows]);

  const save = useMutation({
    mutationFn: () =>
      api.saveScoreEntries(set.id, {
        mode,
        rows: rows
          .filter((r) => r.studentKey.trim())
          .map((r) => ({
            studentKey: r.studentKey.trim(),
            values: Object.fromEntries(columns.map((c) => [c.chunkId, r.values[c.chunkId]?.trim() || null])),
          })),
      }),
    onSuccess: (res) => {
      toast(`${res.rows}명의 성적을 저장했습니다`);
      setDirty(false);
      setError(null);
      qc.invalidateQueries({ queryKey: ['score-entries', set.id] });
      qc.invalidateQueries({ queryKey: ['score-stats', set.id] });
      onChanged();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const emptyRows = rows.filter((r) => !r.studentKey.trim() && Object.values(r.values).some((v) => v.trim())).length;

  if (entriesQuery.isLoading) return <Loading />;

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-2 flex-wrap">
        <Segmented
          label="보기"
          value={view}
          onChange={setView}
          options={[
            { value: 'grid', label: '성적 표' },
            { value: 'stats', label: '이 세트 통계' },
          ]}
        />
        {view === 'grid' && (
          <>
            <Segmented
              label="입력 방식"
              value={mode}
              onChange={switchMode}
              options={[
                { value: 'SCORE', label: '득점 입력' },
                { value: 'RESPONSE', label: '응답 입력 (자동 채점)' },
              ]}
            />
            <span className="text-[11px] text-slate-400">
              {mode === 'SCORE' ? '칸에 득점(숫자)을 넣습니다.' : '칸에 학생 응답(①, 3, 정답 문자열)을 넣으면 정답과 비교해 채점합니다.'}
            </span>
            <div className="ml-auto flex gap-2">
              <Button onClick={() => setImportOpen(true)}>CSV·엑셀 붙여넣기</Button>
              <Button
                onClick={() => {
                  setRows((rs) => [...rs, { studentKey: '', values: {} }]);
                  setDirty(true);
                }}
              >
                + 학생
              </Button>
              <Button variant="primary" disabled={!dirty || save.isPending || duplicateKeys.size > 0} onClick={() => save.mutate()}>
                {save.isPending ? '저장 중…' : '저장'}
              </Button>
            </div>
          </>
        )}
      </div>
      <ErrorBanner message={error ?? (entriesQuery.error ? errorMessage(entriesQuery.error) : null)} />
      {duplicateKeys.size > 0 && (
        <div className="text-xs text-red-600">학생 키가 겹칩니다: {[...duplicateKeys].join(', ')}</div>
      )}
      {emptyRows > 0 && <div className="text-xs text-amber-700">학생 키가 빈 행 {emptyRows}개는 저장되지 않습니다.</div>}

      {view === 'stats' ? (
        statsQuery.isLoading ? (
          <Loading />
        ) : (
          <div className="space-y-4">
            <ExamStatView stat={statsQuery.data?.stat ?? null} />
            <ItemStatsTable
              items={columns.map((c) => ({ ...c, stat: statsQuery.data?.items.find((s) => s.chunkId === c.chunkId) ?? null }))}
            />
          </div>
        )
      ) : columns.length === 0 ? (
        <div className="text-sm text-slate-500">문항(QUESTION 청크)이 없습니다. 자료 화면에서 문항을 먼저 정리하세요.</div>
      ) : (
        <div className="overflow-auto max-h-[60vh] border border-slate-200 rounded-lg bg-white">
          <table className="text-xs border-collapse">
            <thead className="sticky top-0 bg-slate-50 z-10">
              <tr>
                <th className="sticky left-0 bg-slate-50 border-b border-r border-slate-200 px-2 py-1.5 text-left min-w-[120px]">학생 키</th>
                {columns.map((c) => (
                  <th key={c.chunkId} className="border-b border-r border-slate-200 px-1 py-1.5 font-medium min-w-[56px]" title={c.preview}>
                    <div>{c.itemNo ?? '?'}</div>
                    <div className="text-[10px] text-slate-400 font-normal">
                      {c.maxScore}점{mode === 'RESPONSE' && c.answer ? ` · ${c.answer}` : ''}
                    </div>
                  </th>
                ))}
                <th className="border-b border-slate-200 px-2" />
              </tr>
            </thead>
            <tbody>
              {rows.map((r, ri) => (
                <tr key={ri} className="hover:bg-slate-50/60">
                  <td className="sticky left-0 bg-white border-b border-r border-slate-100 p-0">
                    <input
                      value={r.studentKey}
                      onChange={(e) => setKey(ri, e.target.value)}
                      onPaste={(e) => paste(ri, -1, e.clipboardData.getData('text')) && e.preventDefault()}
                      aria-label={`${ri + 1}행 학생 키`}
                      className={clsx(
                        'w-full px-2 py-1 outline-none focus:bg-indigo-50',
                        duplicateKeys.has(r.studentKey.trim()) && 'text-red-600',
                      )}
                    />
                  </td>
                  {columns.map((c, ci) => {
                    const v = r.values[c.chunkId] ?? '';
                    const bad = mode === 'SCORE' && v.trim() !== '' && (Number.isNaN(Number(v)) || Number(v) < 0 || Number(v) > c.maxScore);
                    return (
                      <td key={c.chunkId} className="border-b border-r border-slate-100 p-0">
                        <input
                          value={v}
                          onChange={(e) => setCell(ri, c.chunkId, e.target.value)}
                          onPaste={(e) => paste(ri, ci, e.clipboardData.getData('text')) && e.preventDefault()}
                          aria-label={`${r.studentKey || ri + 1} ${c.itemNo ?? ''}번`}
                          className={clsx('w-full px-1 py-1 text-center outline-none focus:bg-indigo-50 tabular-nums', bad && 'bg-red-50 text-red-700')}
                        />
                      </td>
                    );
                  })}
                  <td className="border-b border-slate-100 px-1">
                    <button
                      type="button"
                      aria-label="행 삭제"
                      className="text-slate-300 hover:text-red-600 px-1"
                      onClick={() => {
                        setRows((rs) => rs.filter((_, i) => i !== ri));
                        setDirty(true);
                      }}
                    >
                      ×
                    </button>
                  </td>
                </tr>
              ))}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={columns.length + 2} className="text-center text-slate-400 py-6">
                    "+ 학생"으로 행을 추가하거나 엑셀 범위를 첫 칸에 붙여넣으세요.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
      <ImportModal
        open={importOpen}
        set={set}
        items={columns}
        onClose={() => setImportOpen(false)}
        onImported={() => {
          setImportOpen(false);
          qc.invalidateQueries({ queryKey: ['score-entries', set.id] });
          qc.invalidateQueries({ queryKey: ['score-stats', set.id] });
          onChanged();
        }}
      />
    </div>
  );
}
