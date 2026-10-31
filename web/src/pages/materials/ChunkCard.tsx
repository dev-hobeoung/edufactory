import clsx from 'clsx';
import { useState } from 'react';
import type { Chunk } from '../../api/types';
import { LabelBadge } from '../../components/LabelBadge';
import { StatBadges } from '../../components/StatBadges';
import { Button, Chip, ConfirmButton } from '../../components/ui';
import { CHUNK_TYPE_LABEL, CHUNK_TYPE_TONE, QUESTION_TYPE_LABEL } from '../../lib/labels';
import { RichText } from '../../lib/math';

export function ChunkCard({
  chunk,
  onEdit,
  onDelete,
  deleting,
}: {
  chunk: Chunk;
  onEdit: () => void;
  onDelete: () => void;
  deleting: boolean;
}) {
  const [showAnswer, setShowAnswer] = useState(false);
  const c = chunk;
  const hasAnswer = !!(c.answer || c.explanation);
  return (
    <div className="bg-white border border-slate-200 rounded-lg p-3 space-y-2 group">
      <div className="flex items-center gap-1.5 flex-wrap text-xs">
        <span className="text-slate-300 tabular-nums w-6">{c.seq}</span>
        <Chip className={CHUNK_TYPE_TONE[c.chunkType]}>{CHUNK_TYPE_LABEL[c.chunkType]}</Chip>
        {c.itemNo && <span className="font-semibold text-slate-700">{c.itemNo}번</span>}
        {c.questionType && <span className="text-slate-500">{QUESTION_TYPE_LABEL[c.questionType]}</span>}
        {c.score !== null && <span className="text-slate-500">{c.score}점</span>}
        {c.title && <span className="font-medium text-slate-700">{c.title}</span>}
        {c.position && <span className="text-slate-400">{c.position}</span>}
        {c.pageFrom !== null && (
          <span className="text-slate-400">
            p.{c.pageFrom}
            {c.pageTo !== null && c.pageTo !== c.pageFrom ? `–${c.pageTo}` : ''}
          </span>
        )}
        {!c.embedded && <Chip className="bg-amber-50 text-amber-700">임베딩 대기</Chip>}
        <StatBadges stat={c.stat} />
        <span className="ml-auto flex gap-1 opacity-60 group-hover:opacity-100">
          <Button size="xs" variant="text" onClick={onEdit}>
            편집
          </Button>
          <ConfirmButton label="삭제" onConfirm={onDelete} disabled={deleting} />
        </span>
      </div>
      {(c.labels.length > 0 || c.tags.length > 0) && (
        <div className="flex flex-wrap gap-1">
          {c.labels.map((l) => (
            <LabelBadge key={l.id} label={l} ai={l.source === 'AI'} compact />
          ))}
          {c.tags.map((t) => (
            <span key={t} className="text-[11px] text-slate-400">
              #{t}
            </span>
          ))}
        </div>
      )}
      <RichText text={c.content} className="text-sm text-slate-800" />
      {c.choices.length > 0 && (
        <ol className="space-y-0.5 text-sm">
          {c.choices.map((ch) => (
            <li key={ch.label} className={clsx('flex gap-1.5', showAnswer && c.answer?.includes(ch.label) && 'font-semibold text-indigo-700')}>
              <span className="shrink-0">{ch.label}</span>
              <RichText text={ch.text} />
            </li>
          ))}
        </ol>
      )}
      {hasAnswer && (
        <div>
          <button type="button" className="text-xs text-indigo-600 hover:underline" onClick={() => setShowAnswer((v) => !v)}>
            {showAnswer ? '정답·해설 접기' : '정답·해설 보기'}
          </button>
          {showAnswer && (
            <div className="mt-1 pl-3 border-l-2 border-indigo-200 text-sm space-y-1">
              {c.answer && (
                <div>
                  <span className="text-xs font-semibold text-indigo-700 mr-1">정답</span>
                  {c.answer}
                </div>
              )}
              {c.explanation && <RichText text={c.explanation} className="text-slate-600" />}
            </div>
          )}
        </div>
      )}
      {c.stat && c.stat.choiceDist.length > 0 && showAnswer && (
        <div className="flex flex-wrap gap-1 text-[11px]">
          {c.stat.choiceDist.map((d) => (
            <span key={d.response} className={clsx('px-1.5 rounded', d.correct ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500')}>
              {d.response}: {d.count}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
