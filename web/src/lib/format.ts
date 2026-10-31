export const pct = (v: number | null | undefined, digits = 0): string =>
  v === null || v === undefined || Number.isNaN(v) ? '–' : `${(v * 100).toFixed(digits)}%`;

export const usd = (v: number | null | undefined, digits = 2): string =>
  v === null || v === undefined ? '–' : `$${v.toFixed(digits)}`;

export const num = (v: number | null | undefined, digits = 0): string =>
  v === null || v === undefined || Number.isNaN(v) ? '–' : v.toLocaleString('ko-KR', { maximumFractionDigits: digits });

export function bytes(v: number | null | undefined): string {
  if (v === null || v === undefined) return '–';
  if (v < 1024) return `${v} B`;
  if (v < 1024 * 1024) return `${(v / 1024).toFixed(1)} KB`;
  return `${(v / 1024 / 1024).toFixed(1)} MB`;
}

export function relTime(iso: string | null | undefined): string {
  if (!iso) return '–';
  const d = new Date(iso);
  const now = new Date();
  const time = d.toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' });
  if (d.toDateString() === now.toDateString()) return `오늘 ${time}`;
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (d.toDateString() === yesterday.toDateString()) return `어제 ${time}`;
  return d.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' });
}

export function dateTime(iso: string | null | undefined): string {
  if (!iso) return '–';
  return new Date(iso).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' });
}

/** 대화 목록 묶음 이름 */
export function dayGroup(iso: string): string {
  const d = new Date(iso);
  const now = new Date();
  if (d.toDateString() === now.toDateString()) return '오늘';
  const diff = (now.getTime() - d.getTime()) / 86_400_000;
  if (diff < 7) return '이번 주';
  if (diff < 31) return '이번 달';
  return '이전';
}

/** 자료 유사도 단계: 경고 기준 이상 빨강, 0.8 이상 주황, 그 밖은 초록 */
export function similarityTone(sim: number | null | undefined, warn = 0.92): 'red' | 'amber' | 'green' | 'none' {
  if (sim === null || sim === undefined) return 'none';
  if (sim >= warn) return 'red';
  if (sim >= Math.min(0.8, warn)) return 'amber';
  return 'green';
}

export const SIM_TONE_CLASS: Record<ReturnType<typeof similarityTone>, string> = {
  red: 'bg-red-50 text-red-700',
  amber: 'bg-amber-50 text-amber-700',
  green: 'bg-emerald-50 text-emerald-700',
  none: 'bg-slate-100 text-slate-500',
};

/** 정답률 색: 낮을수록(어려울수록) 붉게 */
export function rateTone(rate: number | null | undefined): string {
  if (rate === null || rate === undefined) return 'bg-slate-100 text-slate-500';
  if (rate < 0.3) return 'bg-red-50 text-red-700';
  if (rate < 0.6) return 'bg-amber-50 text-amber-700';
  return 'bg-emerald-50 text-emerald-700';
}

/** 변별도 색: 0.3 이상 좋음, 0.2 미만 낮음 */
export function discTone(d: number | null | undefined): string {
  if (d === null || d === undefined) return 'text-slate-400';
  if (d >= 0.3) return 'text-emerald-700';
  if (d >= 0.2) return 'text-amber-700';
  return 'text-red-600';
}
