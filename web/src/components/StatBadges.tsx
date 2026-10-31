import type { ItemStat } from '../api/types';
import { discTone, num, pct, rateTone } from '../lib/format';
import { Chip } from './ui';

/** 문항 통계 요약 칩: 정답률 · 변별도 · 응답 수 (표본 적음) */
export function StatBadges({ stat }: { stat: ItemStat | null }) {
  if (!stat) return null;
  return (
    <span className="inline-flex flex-wrap gap-1 items-center">
      <Chip className={rateTone(stat.avgRate)} title="정답률 (평균 득점 ÷ 배점)">
        정답률 {pct(stat.avgRate)}
      </Chip>
      {stat.discrimination !== null && (
        <span className={`text-xs font-semibold ${discTone(stat.discrimination)}`} title="변별도 (상위 27% − 하위 27%)">
          변별 {stat.discrimination.toFixed(2)}
        </span>
      )}
      <span className="text-xs text-slate-400">n={num(stat.n)}</span>
      {stat.smallSample && <Chip className="bg-amber-50 text-amber-700">표본 적음</Chip>}
    </span>
  );
}
