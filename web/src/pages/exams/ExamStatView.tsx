import type { ExamStat } from '../../api/types';
import { Histogram } from '../../components/Histogram';
import { Stat } from '../../components/ui';
import { num, pct } from '../../lib/format';

export function ExamStatView({ stat }: { stat: ExamStat | null }) {
  if (!stat || stat.n === 0) return <div className="text-sm text-slate-400">아직 성적이 없습니다.</div>;
  return (
    <div className="grid lg:grid-cols-[1fr_auto] gap-4 items-start">
      <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
        <Stat label="응시 인원" value={num(stat.n)} sub={stat.n < 30 ? '표본 적음' : undefined} />
        <Stat label="평균" value={`${num(stat.mean, 1)} / ${num(stat.maxTotal, 1)}`} sub={`득점률 ${pct(stat.meanRate)}`} />
        <Stat label="표준편차" value={stat.std === null ? '–' : num(stat.std, 2)} />
        <Stat label="중앙값" value={num(stat.p50, 1)} sub={`p25 ${num(stat.p25, 1)} · p75 ${num(stat.p75, 1)}`} />
        <Stat label="하위 10%" value={num(stat.p10, 1)} />
        <Stat label="상위 10%" value={num(stat.p90, 1)} />
      </div>
      <div className="bg-white border border-slate-200 rounded-lg px-3 py-2">
        <div className="text-xs text-slate-500 mb-1">총점 분포 (득점률 %)</div>
        <Histogram bins={stat.histogram} label="총점 분포" />
      </div>
    </div>
  );
}
