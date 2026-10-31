/** 간단한 막대 그래프 (총점 비율 10구간 등). 값이 없으면 빈 막대. */
export function Histogram({
  bins,
  height = 120,
  label,
}: {
  bins: { from: number; to: number; count: number }[];
  height?: number;
  label: string;
}) {
  const max = Math.max(1, ...bins.map((b) => b.count));
  const w = 360;
  const pad = 22;
  const barW = bins.length ? (w - pad) / bins.length : 0;
  const h = height;
  return (
    <svg viewBox={`0 0 ${w} ${h + 18}`} className="w-full max-w-md" role="img" aria-label={label}>
      <line x1={pad} y1={h} x2={w} y2={h} stroke="var(--axis)" strokeWidth={1} />
      {bins.map((b, i) => {
        const bh = (b.count / max) * (h - 14);
        const x = pad + i * barW;
        return (
          <g key={i}>
            <rect x={x + 2} y={h - bh} width={Math.max(1, barW - 4)} height={bh} rx={2} fill="var(--series-1)">
              <title>{`${Math.round(b.from * 100)}~${Math.round(b.to * 100)}% · ${b.count}명`}</title>
            </rect>
            {b.count > 0 && (
              <text x={x + barW / 2} y={h - bh - 3} textAnchor="middle" fontSize={9} fill="var(--ink-muted)">
                {b.count}
              </text>
            )}
            {i % 2 === 0 && (
              <text x={x} y={h + 12} fontSize={9} fill="var(--ink-muted)">
                {Math.round(b.from * 100)}
              </text>
            )}
          </g>
        );
      })}
      <text x={w} y={h + 12} fontSize={9} textAnchor="end" fill="var(--ink-muted)">
        100%
      </text>
    </svg>
  );
}

/** 가로 막대 (일별 비용 등) */
export function Bars({ data, format }: { data: { label: string; value: number }[]; format: (v: number) => string }) {
  const max = Math.max(0.0001, ...data.map((d) => d.value));
  return (
    <div className="flex items-end gap-0.5 h-24" role="img" aria-label="막대 그래프">
      {data.map((d) => (
        <div key={d.label} className="flex-1 flex flex-col justify-end h-full group relative">
          <div className="bg-indigo-400 rounded-t" style={{ height: `${(d.value / max) * 100}%`, minHeight: d.value > 0 ? 2 : 0 }} />
          <div className="hidden group-hover:block absolute bottom-full mb-1 left-1/2 -translate-x-1/2 bg-slate-900 text-white text-[10px] px-1.5 py-0.5 rounded whitespace-nowrap z-10">
            {d.label} · {format(d.value)}
          </div>
        </div>
      ))}
    </div>
  );
}
