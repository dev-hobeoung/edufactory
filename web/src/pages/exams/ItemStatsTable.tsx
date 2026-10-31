import clsx from 'clsx';
import { Fragment, useState } from 'react';
import type { ExamItem } from '../../api/types';
import { Chip } from '../../components/ui';
import { discTone, num, pct, rateTone } from '../../lib/format';
import { QUESTION_TYPE_LABEL } from '../../lib/labels';

/** 문항별 통계 표 — 행을 누르면 선택지(응답) 분포 */
export function ItemStatsTable({ items }: { items: ExamItem[] }) {
  const [open, setOpen] = useState<string | null>(null);
  return (
    <div className="bg-white border border-slate-200 rounded-lg overflow-x-auto">
      <table className="w-full text-sm">
        <thead className="bg-slate-50 text-xs text-slate-500">
          <tr>
            <th className="text-left px-3 py-2 font-medium w-16">번호</th>
            <th className="text-left px-3 py-2 font-medium">문항</th>
            <th className="text-left px-3 py-2 font-medium">유형</th>
            <th className="text-right px-3 py-2 font-medium">배점</th>
            <th className="text-left px-3 py-2 font-medium">정답</th>
            <th className="text-right px-3 py-2 font-medium">정답률</th>
            <th className="text-right px-3 py-2 font-medium" title="상위 27% − 하위 27% 정답률">
              변별도
            </th>
            <th className="text-right px-3 py-2 font-medium">만점률</th>
            <th className="text-right px-3 py-2 font-medium">0점</th>
            <th className="text-right px-3 py-2 font-medium">n</th>
          </tr>
        </thead>
        <tbody>
          {items.map((it) => {
            const s = it.stat;
            const hasDist = !!s && s.choiceDist.length > 0;
            const total = s ? s.choiceDist.reduce((a, d) => a + d.count, 0) : 0;
            return (
              <Fragment key={it.chunkId}>
                <tr
                  className={clsx('border-t border-slate-100', hasDist && 'cursor-pointer hover:bg-slate-50')}
                  onClick={() => hasDist && setOpen(open === it.chunkId ? null : it.chunkId)}
                >
                  <td className="px-3 py-2 font-semibold">{it.itemNo ?? '–'}</td>
                  <td className="px-3 py-2 text-slate-600 max-w-[360px] truncate" title={it.preview}>
                    {it.preview}
                  </td>
                  <td className="px-3 py-2 text-xs text-slate-500 whitespace-nowrap">{it.questionType ? QUESTION_TYPE_LABEL[it.questionType] : '–'}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{num(it.maxScore, 1)}</td>
                  <td className="px-3 py-2 text-xs">{it.answer ?? <span className="text-slate-300">–</span>}</td>
                  <td className="px-3 py-2 text-right">
                    {s ? <Chip className={rateTone(s.avgRate)}>{pct(s.avgRate)}</Chip> : <span className="text-slate-300">–</span>}
                  </td>
                  <td className={clsx('px-3 py-2 text-right tabular-nums font-semibold', discTone(s?.discrimination))}>
                    {s?.discrimination === null || s?.discrimination === undefined ? '–' : s.discrimination.toFixed(2)}
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums text-slate-600">{s ? pct(s.fullRate) : '–'}</td>
                  <td className="px-3 py-2 text-right tabular-nums text-slate-600">{s ? pct(s.zeroRate) : '–'}</td>
                  <td className="px-3 py-2 text-right tabular-nums text-slate-500">
                    {s ? num(s.n) : '–'}
                    {s?.smallSample && <span className="ml-1 text-[10px] text-amber-600">적음</span>}
                    {hasDist && <span className="ml-1 text-slate-300">{open === it.chunkId ? '▴' : '▾'}</span>}
                  </td>
                </tr>
                {open === it.chunkId && s && (
                  <tr className="bg-slate-50/60">
                    <td />
                    <td colSpan={9} className="px-3 py-2">
                      <div className="text-xs text-slate-500 mb-1">응답 분포</div>
                      <div className="space-y-1 max-w-md">
                        {s.choiceDist.map((d) => (
                          <div key={d.response} className="flex items-center gap-2 text-xs">
                            <span className={clsx('w-16 truncate', d.correct && 'font-semibold text-emerald-700')}>
                              {d.response}
                              {d.correct ? ' ✓' : ''}
                            </span>
                            <div className="flex-1 h-2 bg-slate-200 rounded">
                              <div
                                className={clsx('h-2 rounded', d.correct ? 'bg-emerald-500' : 'bg-slate-400')}
                                style={{ width: `${total ? (d.count / total) * 100 : 0}%` }}
                              />
                            </div>
                            <span className="w-16 text-right tabular-nums text-slate-500">
                              {d.count} ({total ? Math.round((d.count / total) * 100) : 0}%)
                            </span>
                          </div>
                        ))}
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
