import clsx from 'clsx';
import type { LabelRef } from '../api/types';
import { DEFAULT_LABEL_COLOR } from '../lib/labels';

/** 라벨 칩 — "그룹 · 이름". source=AI 면 점선 테두리 */
export function LabelBadge({
  label,
  ai = false,
  onRemove,
  compact = false,
}: {
  label: LabelRef;
  ai?: boolean;
  onRemove?: () => void;
  compact?: boolean;
}) {
  const color = label.color || DEFAULT_LABEL_COLOR;
  return (
    <span
      title={ai ? `${label.group} · ${label.name} (AI 부여)` : `${label.group} · ${label.name}`}
      className={clsx(
        'inline-flex items-center gap-1 rounded-full text-xs whitespace-nowrap border',
        compact ? 'px-1.5 py-0' : 'px-2 py-0.5',
        ai ? 'border-dashed' : 'border-transparent',
      )}
      style={{ backgroundColor: `${color}14`, color, borderColor: ai ? color : 'transparent' }}
    >
      <span className="opacity-70">{label.group}</span>
      <span className="font-semibold">{label.name}</span>
      {ai && <span className="text-[9px] font-bold opacity-80">AI</span>}
      {onRemove && (
        <button type="button" onClick={onRemove} aria-label={`${label.name} 라벨 빼기`} className="ml-0.5 hover:opacity-60">
          ×
        </button>
      )}
    </span>
  );
}

export function LabelList({ labels, max }: { labels: (LabelRef & { source?: 'MANUAL' | 'AI' })[]; max?: number }) {
  const shown = max ? labels.slice(0, max) : labels;
  return (
    <span className="inline-flex flex-wrap gap-1">
      {shown.map((l) => (
        <LabelBadge key={l.id} label={l} ai={l.source === 'AI'} compact />
      ))}
      {max && labels.length > max && <span className="text-xs text-slate-400">+{labels.length - max}</span>}
    </span>
  );
}
