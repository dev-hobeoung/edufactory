import { useState } from 'react';
import type { Chunk, ChunkInput, ChunkType, QuestionType } from '../../api/types';
import { LabelPicker, TagInput } from '../../components/pickers';
import { Button, Field, inputClass, textareaClass } from '../../components/ui';
import { CHOICE_TYPES, CHUNK_TYPES, CHUNK_TYPE_LABEL, QUESTION_TYPES, QUESTION_TYPE_LABEL } from '../../lib/labels';
import { RichText } from '../../lib/math';

export function emptyChunkInput(isExam: boolean): ChunkInput {
  return {
    chunkType: isExam ? 'QUESTION' : 'SECTION',
    title: null,
    position: null,
    itemNo: null,
    questionType: isExam ? 'MULTIPLE_CHOICE' : null,
    content: '',
    choices: [],
    answer: null,
    explanation: null,
    score: null,
    tags: [],
    labelIds: [],
  };
}

export function chunkToInput(c: Chunk): ChunkInput {
  return {
    chunkType: c.chunkType,
    title: c.title,
    position: c.position,
    itemNo: c.itemNo,
    questionType: c.questionType,
    content: c.content,
    choices: c.choices,
    answer: c.answer,
    explanation: c.explanation,
    score: c.score,
    tags: c.tags,
    labelIds: c.labels.map((l) => l.id),
  };
}

const CIRCLED = ['①', '②', '③', '④', '⑤', '⑥', '⑦', '⑧'];

/** 청크 편집 — 본문은 마크다운+LaTeX, 오른쪽에 미리보기 */
export function ChunkEditor({
  initial,
  aiLabelIds = [],
  onSave,
  onCancel,
  saving,
}: {
  initial: ChunkInput;
  aiLabelIds?: string[];
  onSave: (input: ChunkInput) => void;
  onCancel: () => void;
  saving: boolean;
}) {
  const [f, setF] = useState<ChunkInput>(initial);
  const set = <K extends keyof ChunkInput>(k: K, v: ChunkInput[K]) => setF((x) => ({ ...x, [k]: v }));
  const isQuestion = f.chunkType === 'QUESTION';
  const usesChoices = isQuestion && f.questionType !== null && CHOICE_TYPES.includes(f.questionType);

  const setChoice = (i: number, patch: Partial<{ label: string; text: string }>) =>
    set(
      'choices',
      f.choices.map((c, j) => (j === i ? { ...c, ...patch } : c)),
    );

  return (
    <div className="border border-indigo-200 bg-indigo-50/30 rounded-lg p-3 space-y-3">
      <div className="grid grid-cols-6 gap-2">
        <Field label="종류">
          <select
            value={f.chunkType}
            onChange={(e) => {
              const t = e.target.value as ChunkType;
              setF((x) => ({ ...x, chunkType: t, questionType: t === 'QUESTION' ? (x.questionType ?? 'MULTIPLE_CHOICE') : null }));
            }}
            className={`${inputClass} w-full text-xs`}
          >
            {CHUNK_TYPES.map((t) => (
              <option key={t} value={t}>
                {CHUNK_TYPE_LABEL[t]}
              </option>
            ))}
          </select>
        </Field>
        {isQuestion && (
          <>
            <Field label="문항 번호">
              <input value={f.itemNo ?? ''} onChange={(e) => set('itemNo', e.target.value || null)} className={`${inputClass} w-full text-xs`} />
            </Field>
            <Field label="문항 유형">
              <select
                value={f.questionType ?? ''}
                onChange={(e) => set('questionType', (e.target.value || null) as QuestionType | null)}
                className={`${inputClass} w-full text-xs`}
              >
                <option value="">미지정</option>
                {QUESTION_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {QUESTION_TYPE_LABEL[t]}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="배점">
              <input
                type="number"
                step="0.5"
                min={0}
                value={f.score ?? ''}
                onChange={(e) => set('score', e.target.value === '' ? null : Number(e.target.value))}
                className={`${inputClass} w-full text-xs`}
              />
            </Field>
          </>
        )}
        <Field label="제목">
          <input value={f.title ?? ''} onChange={(e) => set('title', e.target.value || null)} className={`${inputClass} w-full text-xs`} />
        </Field>
        <Field label="위치">
          <input
            value={f.position ?? ''}
            onChange={(e) => set('position', e.target.value || null)}
            placeholder="예: p.4, 2장"
            className={`${inputClass} w-full text-xs`}
          />
        </Field>
      </div>
      <div className="grid grid-cols-2 gap-3">
        <Field label="본문 (마크다운 + $LaTeX$)">
          <textarea value={f.content} onChange={(e) => set('content', e.target.value)} rows={8} className={`${textareaClass} text-sm font-mono`} />
        </Field>
        <div className="space-y-1">
          <span className="text-xs font-medium text-slate-500">미리보기</span>
          <div className="border border-slate-200 bg-white rounded-lg p-2 text-sm min-h-[160px] max-h-[220px] overflow-y-auto">
            <RichText text={f.content || ' '} />
          </div>
        </div>
      </div>
      {usesChoices && (
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <span className="text-xs font-medium text-slate-500">선택지</span>
            <Button
              size="xs"
              variant="text"
              onClick={() => set('choices', [...f.choices, { label: CIRCLED[f.choices.length] ?? String(f.choices.length + 1), text: '' }])}
            >
              + 선택지
            </Button>
          </div>
          {f.choices.map((c, i) => (
            <div key={i} className="flex gap-2">
              <input
                value={c.label}
                onChange={(e) => setChoice(i, { label: e.target.value })}
                aria-label="선택지 기호"
                className={`${inputClass} w-14 text-xs text-center`}
              />
              <input
                value={c.text}
                onChange={(e) => setChoice(i, { text: e.target.value })}
                aria-label="선택지 내용"
                className={`${inputClass} flex-1 text-xs`}
              />
              <Button size="xs" variant="text" onClick={() => set('choices', f.choices.filter((_, j) => j !== i))}>
                삭제
              </Button>
            </div>
          ))}
        </div>
      )}
      {isQuestion && (
        <div className="grid grid-cols-2 gap-3">
          <Field label="정답" hint={usesChoices ? '선택지 기호, 복수 정답은 쉼표 (예: ①,③)' : undefined}>
            <input value={f.answer ?? ''} onChange={(e) => set('answer', e.target.value || null)} className={`${inputClass} w-full text-sm`} />
          </Field>
          <Field label="해설">
            <textarea
              value={f.explanation ?? ''}
              onChange={(e) => set('explanation', e.target.value || null)}
              rows={2}
              className={`${textareaClass} text-sm`}
            />
          </Field>
        </div>
      )}
      <div className="grid grid-cols-2 gap-3">
        <div className="space-y-1">
          <span className="text-xs font-medium text-slate-500">청크 라벨 (점선 = AI 부여, 저장하면 수동으로 확정)</span>
          <LabelPicker value={f.labelIds} onChange={(ids) => set('labelIds', ids)} aiIds={aiLabelIds} />
        </div>
        <div className="space-y-1">
          <span className="text-xs font-medium text-slate-500">태그</span>
          <TagInput value={f.tags} onChange={(v) => set('tags', v)} />
        </div>
      </div>
      <div className="flex justify-end gap-2">
        <Button onClick={onCancel}>취소</Button>
        <Button variant="primary" onClick={() => onSave(f)} disabled={!f.content.trim() || saving}>
          {saving ? '저장 중…' : '저장 (재임베딩)'}
        </Button>
      </div>
    </div>
  );
}
