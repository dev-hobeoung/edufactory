import clsx from 'clsx';
import { useState } from 'react';
import type { AppliedRule, Conditions, Label, ToolStep, Turn } from '../../api/types';
import { Chip, Spinner } from '../../components/ui';
import { CREATIVITY_LEVELS, OUTPUT_KIND_LABEL, QUESTION_TYPE_LABEL } from '../../lib/labels';
import { pct } from '../../lib/format';
import { RichText } from '../../lib/math';
import { useLabels } from '../../lib/queries';

function ToolChip({ step }: { step: ToolStep }) {
  const running = step.status === 'started';
  const failed = step.status === 'error';
  return (
    <div className={clsx('flex items-center gap-2 text-xs', running ? 'text-indigo-700' : 'text-slate-500')}>
      <span
        aria-hidden
        className={clsx(
          'w-4 h-4 rounded-full flex items-center justify-center text-[10px] shrink-0',
          running && 'bg-indigo-100 animate-pulse',
          failed && 'bg-red-100 text-red-700',
          !running && !failed && 'bg-emerald-100 text-emerald-700',
        )}
      >
        {running ? '●' : failed ? '!' : '✓'}
      </span>
      <span className="font-medium">{step.label || step.name}</span>
      {step.summary && <span className={clsx(failed ? 'text-red-500' : 'text-slate-400')}>{step.summary}</span>}
      {running && !step.summary && <span className="text-slate-400">진행 중…</span>}
    </div>
  );
}

/** "규칙 n개 적용" — 펼치면 규칙별 주입량 */
export function ContextChip({ rules }: { rules: AppliedRule[] }) {
  const [open, setOpen] = useState(false);
  if (rules.length === 0) return null;
  return (
    <div className="w-fit max-w-full">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="inline-flex items-center gap-1.5 text-xs font-medium text-violet-700 bg-violet-50 hover:bg-violet-100 rounded-full px-2.5 py-1"
      >
        <span aria-hidden>◆</span>규칙 {rules.length}개 적용
        <span aria-hidden className="text-violet-400">
          {open ? '▴' : '▾'}
        </span>
      </button>
      {open && (
        <ul className="mt-1.5 bg-white border border-violet-100 rounded-xl px-3 py-2 space-y-1 text-xs text-slate-600">
          {rules.map((r) => (
            <li key={r.id} className="flex gap-3">
              <span className="flex-1 font-medium">{r.name}</span>
              <span className="text-slate-400 tabular-nums">
                자료 {r.materialCount} · 검색 {r.chunkCount} · {r.chars.toLocaleString()}자
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Thinking({ text, live }: { text: string; live: boolean }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="w-fit max-w-[85%]">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="text-xs text-slate-400 hover:text-slate-600 inline-flex items-center gap-1"
      >
        <span className={clsx(live && 'animate-pulse')}>{live ? '생각하는 중' : '추론 요약'}</span>
        <span aria-hidden>{open ? '▴' : '▾'}</span>
      </button>
      {open && (
        <div className="mt-1 border-l-2 border-slate-200 pl-3 text-xs text-slate-500 whitespace-pre-wrap leading-relaxed">{text}</div>
      )}
    </div>
  );
}

/** 생성 중 표시 — 도구 결과를 기다리는 동안에도 멈춘 것처럼 보이지 않게 */
function LiveStatus({ turn }: { turn: Turn }) {
  const runningTool = [...turn.tools].reverse().find((t) => t.status === 'started');
  const label = runningTool
    ? `${runningTool.label || runningTool.name} — 진행 중…`
    : !turn.text && turn.tools.length === 0 && !turn.thinking
      ? '요청을 읽고 있습니다…'
      : '답변을 만드는 중…';
  return (
    <div className="flex items-center gap-2 text-xs text-slate-500" role="status">
      <Spinner className="text-indigo-500" />
      <span>{label}</span>
    </div>
  );
}

export function conditionsLabel(c: Conditions | null, labels: Label[]): string[] {
  if (!c) return [];
  const out: string[] = [];
  if (c.outputKind) out.push(OUTPUT_KIND_LABEL[c.outputKind]);
  if (c.labelIds?.length)
    out.push(
      c.labelIds
        .map((id) => labels.find((l) => l.id === id))
        .filter(Boolean)
        .map((l) => `${l!.group}:${l!.name}`)
        .join(', '),
    );
  if (c.materialIds?.length) out.push(`자료 ${c.materialIds.length}개`);
  if (c.questionTypes?.length) out.push(c.questionTypes.map((q) => QUESTION_TYPE_LABEL[q]).join('·'));
  if (c.itemCount !== null) out.push(`${c.itemCount}문항`);
  if (c.totalScore !== null) out.push(`총점 ${c.totalScore}`);
  if (c.targetCorrectRate !== null) out.push(`정답률 ${pct(c.targetCorrectRate)}`);
  if (c.audience) out.push(c.audience);
  if (c.creativity) out.push(`외부 자료 ${CREATIVITY_LEVELS[c.creativity] ?? c.creativity}`);
  if (c.extra) out.push(c.extra);
  return out.filter(Boolean);
}

export function TurnView({
  turn,
  live = false,
  onSelectVersion,
}: {
  turn: Turn;
  live?: boolean;
  onSelectVersion: (v: number) => void;
}) {
  const { data: labels = [] } = useLabels();
  if (turn.role === 'user') {
    const cond = conditionsLabel(turn.conditions, labels);
    return (
      <div className="flex flex-col items-end gap-1">
        <div className="max-w-[78%] bg-indigo-600 text-white rounded-2xl rounded-br-md px-4 py-2.5 text-sm whitespace-pre-wrap break-words">
          {turn.text}
        </div>
        {cond.length > 0 && <div className="text-[11px] text-slate-400 max-w-[78%] text-right">{cond.join(' · ')}</div>}
      </div>
    );
  }

  const hasText = turn.text.trim().length > 0;
  return (
    <div className="space-y-2.5">
      <ContextChip rules={turn.context ?? []} />
      {turn.thinking && <Thinking text={turn.thinking} live={live && !hasText && turn.tools.length === 0} />}
      {turn.tools.length > 0 && (
        <div className="bg-white border border-slate-200 rounded-xl px-3 py-2.5 space-y-1.5 w-fit max-w-full">
          {turn.tools.map((t) => (
            <ToolChip key={t.id} step={t} />
          ))}
        </div>
      )}
      {hasText && (
        <div className="max-w-[85%] bg-white border border-slate-200 rounded-2xl rounded-bl-md px-4 py-3 text-sm text-slate-700">
          <RichText text={turn.text} />
          {live && <span className="inline-block w-1.5 h-4 bg-indigo-400 animate-pulse align-middle ml-0.5" />}
        </div>
      )}
      {live && !turn.stopped && <LiveStatus turn={turn} />}
      {((turn.documentVersions ?? []).length > 0 || turn.stopped) && (
        <div className="flex flex-wrap gap-1.5">
          {(turn.documentVersions ?? []).map((v) => (
            <button
              key={v}
              type="button"
              onClick={() => onSelectVersion(v)}
              className="text-xs font-medium text-indigo-700 bg-indigo-50 hover:bg-indigo-100 rounded-full px-2.5 py-1"
            >
              산출물 v{v} 보기
            </button>
          ))}
          {turn.stopped && <Chip className="bg-slate-100 text-slate-500">중단됨</Chip>}
        </div>
      )}
    </div>
  );
}
