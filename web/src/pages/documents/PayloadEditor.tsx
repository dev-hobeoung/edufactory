import clsx from 'clsx';
import { useState } from 'react';
import type { DocPayload, Item, OutputKind, QuestionType, Section } from '../../api/types';
import { TagInput } from '../../components/pickers';
import { Button, Field, inputClass, textareaClass } from '../../components/ui';
import { CHOICE_TYPES, OUTPUT_KINDS, OUTPUT_KIND_LABEL, QUESTION_TYPES, QUESTION_TYPE_LABEL } from '../../lib/labels';

const CIRCLED = ['①', '②', '③', '④', '⑤', '⑥', '⑦', '⑧'];

export function emptyItem(number: string): Item {
  return {
    number,
    type: 'MULTIPLE_CHOICE',
    stem: '',
    choices: CIRCLED.slice(0, 5).map((label) => ({ label, text: '' })),
    answer: '',
    explanation: null,
    score: null,
    criteria: [],
    difficulty: null,
    tags: [],
    sourceRefs: [],
  };
}

function move<T>(list: T[], i: number, d: number): T[] {
  const j = i + d;
  if (j < 0 || j >= list.length) return list;
  const next = [...list];
  [next[i], next[j]] = [next[j], next[i]];
  return next;
}

/** 서버 검증 전에 화면에서 보이는 문제 */
export function payloadProblems(p: DocPayload): string[] {
  const out: string[] = [];
  if (!p.title.trim()) out.push('제목이 비었습니다');
  const numbers = new Set<string>();
  p.sections.forEach((s) =>
    s.items.forEach((it) => {
      const n = it.number.trim();
      if (!n) out.push('번호가 빈 문항이 있습니다');
      else if (numbers.has(n)) out.push(`문항 번호 ${n} 가 겹칩니다`);
      numbers.add(n);
      if (!it.stem.trim()) out.push(`${n || '?'}번 발문이 비었습니다`);
      if (CHOICE_TYPES.includes(it.type) && it.type !== 'TRUE_FALSE' && it.choices.length < 2)
        out.push(`${n}번 선택지가 2개 미만입니다`);
      if (it.criteria.length > 0 && it.score !== null) {
        const sum = it.criteria.reduce((a, c) => a + (Number(c.score) || 0), 0);
        if (Math.abs(sum - it.score) > 1e-6) out.push(`${n}번 채점기준 합(${sum})이 배점(${it.score})과 다릅니다`);
      }
    }),
  );
  return out;
}

function ItemEditor({
  item,
  onChange,
  onRemove,
  onMove,
}: {
  item: Item;
  onChange: (it: Item) => void;
  onRemove: () => void;
  onMove: (d: number) => void;
}) {
  const [open, setOpen] = useState(!item.stem);
  const set = <K extends keyof Item>(k: K, v: Item[K]) => onChange({ ...item, [k]: v });
  const usesChoices = CHOICE_TYPES.includes(item.type);
  return (
    <div className="border border-slate-200 rounded-lg bg-white">
      <div className="flex items-center gap-2 px-2.5 py-1.5">
        <button type="button" onClick={() => setOpen((v) => !v)} className="flex-1 min-w-0 flex items-center gap-2 text-left" aria-expanded={open}>
          <span className="font-semibold text-sm w-10 shrink-0">{item.number || '?'}.</span>
          <span className="text-[11px] text-slate-400 shrink-0">{QUESTION_TYPE_LABEL[item.type]}</span>
          <span className="text-sm text-slate-600 truncate">{item.stem || '(발문 없음)'}</span>
          {item.score !== null && <span className="text-xs text-slate-400 shrink-0">{item.score}점</span>}
        </button>
        <Button size="xs" variant="text" onClick={() => onMove(-1)} aria-label="위로">
          ↑
        </Button>
        <Button size="xs" variant="text" onClick={() => onMove(1)} aria-label="아래로">
          ↓
        </Button>
        <Button size="xs" variant="text" onClick={onRemove}>
          삭제
        </Button>
      </div>
      {open && (
        <div className="border-t border-slate-100 p-3 space-y-2.5">
          <div className="grid grid-cols-6 gap-2">
            <Field label="번호">
              <input value={item.number} onChange={(e) => set('number', e.target.value)} className={`${inputClass} w-full text-xs`} />
            </Field>
            <div className="col-span-2">
              <Field label="유형">
                <select
                  value={item.type}
                  onChange={(e) => {
                    const t = e.target.value as QuestionType;
                    onChange({
                      ...item,
                      type: t,
                      choices:
                        t === 'TRUE_FALSE'
                          ? [
                              { label: 'O', text: '참' },
                              { label: 'X', text: '거짓' },
                            ]
                          : CHOICE_TYPES.includes(t)
                            ? item.choices
                            : [],
                    });
                  }}
                  className={`${inputClass} w-full text-xs`}
                >
                  {QUESTION_TYPES.map((t) => (
                    <option key={t} value={t}>
                      {QUESTION_TYPE_LABEL[t]}
                    </option>
                  ))}
                </select>
              </Field>
            </div>
            <Field label="배점">
              <input
                type="number"
                step="0.5"
                min={0}
                value={item.score ?? ''}
                onChange={(e) => set('score', e.target.value === '' ? null : Number(e.target.value))}
                className={`${inputClass} w-full text-xs`}
              />
            </Field>
            <div className="col-span-2">
              <Field label="예상 정답률 (%)">
                <input
                  type="number"
                  min={0}
                  max={100}
                  value={item.difficulty === null ? '' : Math.round(item.difficulty * 100)}
                  onChange={(e) => set('difficulty', e.target.value === '' ? null : Math.max(0, Math.min(100, Number(e.target.value))) / 100)}
                  className={`${inputClass} w-full text-xs`}
                />
              </Field>
            </div>
          </div>
          <Field label={item.type === 'FLASHCARD' ? '앞면 (질문)' : '발문'}>
            <textarea value={item.stem} onChange={(e) => set('stem', e.target.value)} rows={3} className={`${textareaClass} text-sm`} />
          </Field>
          {usesChoices && (
            <div className="space-y-1">
              <div className="flex items-center justify-between">
                <span className="text-xs font-medium text-slate-500">선택지</span>
                <Button
                  size="xs"
                  variant="text"
                  onClick={() => set('choices', [...item.choices, { label: CIRCLED[item.choices.length] ?? String(item.choices.length + 1), text: '' }])}
                >
                  + 선택지
                </Button>
              </div>
              {item.choices.map((c, i) => (
                <div key={i} className="flex gap-1.5">
                  <input
                    value={c.label}
                    onChange={(e) => set('choices', item.choices.map((x, j) => (j === i ? { ...x, label: e.target.value } : x)))}
                    aria-label="선택지 기호"
                    className={`${inputClass} w-12 text-xs text-center`}
                  />
                  <input
                    value={c.text}
                    onChange={(e) => set('choices', item.choices.map((x, j) => (j === i ? { ...x, text: e.target.value } : x)))}
                    aria-label="선택지 내용"
                    className={`${inputClass} flex-1 text-xs`}
                  />
                  <Button size="xs" variant="text" onClick={() => set('choices', item.choices.filter((_, j) => j !== i))}>
                    ×
                  </Button>
                </div>
              ))}
            </div>
          )}
          <div className="grid grid-cols-2 gap-2">
            <Field label={item.type === 'FLASHCARD' ? '뒷면 (답)' : '정답'} hint={usesChoices ? '선택지 기호, 복수는 쉼표' : undefined}>
              <textarea value={item.answer} onChange={(e) => set('answer', e.target.value)} rows={2} className={`${textareaClass} text-sm`} />
            </Field>
            <Field label="해설">
              <textarea
                value={item.explanation ?? ''}
                onChange={(e) => set('explanation', e.target.value || null)}
                rows={2}
                className={`${textareaClass} text-sm`}
              />
            </Field>
          </div>
          <div className="space-y-1">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-500">
                채점 기준 {item.criteria.length > 0 && `(합 ${item.criteria.reduce((a, c) => a + (Number(c.score) || 0), 0)}점)`}
              </span>
              <Button size="xs" variant="text" onClick={() => set('criteria', [...item.criteria, { text: '', score: 1 }])}>
                + 기준
              </Button>
            </div>
            {item.criteria.map((c, i) => (
              <div key={i} className="flex gap-1.5">
                <input
                  value={c.text}
                  onChange={(e) => set('criteria', item.criteria.map((x, j) => (j === i ? { ...x, text: e.target.value } : x)))}
                  aria-label="채점 기준"
                  className={`${inputClass} flex-1 text-xs`}
                />
                <input
                  type="number"
                  step="0.5"
                  value={c.score}
                  onChange={(e) => set('criteria', item.criteria.map((x, j) => (j === i ? { ...x, score: Number(e.target.value) || 0 } : x)))}
                  aria-label="기준 배점"
                  className={`${inputClass} w-16 text-xs`}
                />
                <Button size="xs" variant="text" onClick={() => set('criteria', item.criteria.filter((_, j) => j !== i))}>
                  ×
                </Button>
              </div>
            ))}
          </div>
          <Field label="태그">
            <TagInput value={item.tags} onChange={(v) => set('tags', v)} />
          </Field>
          {item.sourceRefs.length > 0 && (
            <div className="text-[11px] text-slate-400 font-mono truncate">참고: {item.sourceRefs.join(', ')}</div>
          )}
        </div>
      )}
    </div>
  );
}

function SectionEditor({
  section,
  index,
  onChange,
  onRemove,
  onMove,
  nextNumber,
}: {
  section: Section;
  index: number;
  onChange: (s: Section) => void;
  onRemove: () => void;
  onMove: (d: number) => void;
  nextNumber: () => string;
}) {
  return (
    <div className="border border-slate-300 rounded-lg p-3 space-y-2 bg-slate-50">
      <div className="flex items-center gap-2">
        <span className="text-xs text-slate-400">섹션 {index + 1}</span>
        <input
          value={section.title ?? ''}
          onChange={(e) => onChange({ ...section, title: e.target.value || null })}
          placeholder="섹션 제목 (선택)"
          aria-label="섹션 제목"
          className={`${inputClass} flex-1 text-sm`}
        />
        <Button size="xs" variant="text" onClick={() => onMove(-1)} aria-label="섹션 위로">
          ↑
        </Button>
        <Button size="xs" variant="text" onClick={() => onMove(1)} aria-label="섹션 아래로">
          ↓
        </Button>
        <Button size="xs" variant="text" onClick={onRemove}>
          섹션 삭제
        </Button>
      </div>
      <textarea
        value={section.body ?? ''}
        onChange={(e) => onChange({ ...section, body: e.target.value || null })}
        rows={section.body ? 5 : 2}
        placeholder="본문 — 지문·개념 설명·학습 내용 (마크다운 + $LaTeX$)"
        aria-label="섹션 본문"
        className={`${textareaClass} text-sm bg-white`}
      />
      <div className="space-y-1.5">
        {section.items.map((it, i) => (
          <ItemEditor
            key={i}
            item={it}
            onChange={(next) => onChange({ ...section, items: section.items.map((x, j) => (j === i ? next : x)) })}
            onRemove={() => onChange({ ...section, items: section.items.filter((_, j) => j !== i) })}
            onMove={(d) => onChange({ ...section, items: move(section.items, i, d) })}
          />
        ))}
      </div>
      <Button size="xs" onClick={() => onChange({ ...section, items: [...section.items, emptyItem(nextNumber())] })}>
        + 문항
      </Button>
    </div>
  );
}

/** 산출물 구조 편집기 — 저장하면 새 버전 */
export function PayloadEditor({ value, onChange }: { value: DocPayload; onChange: (p: DocPayload) => void }) {
  const p = value;
  const set = <K extends keyof DocPayload>(k: K, v: DocPayload[K]) => onChange({ ...p, [k]: v });
  const count = p.sections.reduce((a, s) => a + s.items.length, 0);
  const nextNumber = () => String(count + 1);

  const renumber = () => {
    let n = 0;
    onChange({ ...p, sections: p.sections.map((s) => ({ ...s, items: s.items.map((it) => ({ ...it, number: String(++n) })) })) });
  };

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-6 gap-2">
        <div className="col-span-4">
          <Field label="제목">
            <input value={p.title} onChange={(e) => set('title', e.target.value)} className={`${inputClass} w-full`} />
          </Field>
        </div>
        <div className="col-span-2">
          <Field label="종류">
            <select value={p.kind} onChange={(e) => set('kind', e.target.value as OutputKind)} className={`${inputClass} w-full`}>
              {OUTPUT_KINDS.map((k) => (
                <option key={k} value={k}>
                  {OUTPUT_KIND_LABEL[k]}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <div className="col-span-6">
          <Field label="대상">
            <input value={p.audience ?? ''} onChange={(e) => set('audience', e.target.value || null)} className={`${inputClass} w-full`} />
          </Field>
        </div>
        <div className="col-span-3">
          <Field label="개요·출제 의도 (정답지에 표시)">
            <textarea value={p.summary ?? ''} onChange={(e) => set('summary', e.target.value || null)} rows={3} className={`${textareaClass} text-sm`} />
          </Field>
        </div>
        <div className="col-span-3">
          <Field label="안내문·학습 목표 (문제지에 표시)">
            <textarea
              value={p.instructions ?? ''}
              onChange={(e) => set('instructions', e.target.value || null)}
              rows={3}
              className={`${textareaClass} text-sm`}
            />
          </Field>
        </div>
        <div className="col-span-6">
          <Field label="변경 메모 (이번 버전에서 바꾼 점)">
            <input value={p.changeNote ?? ''} onChange={(e) => set('changeNote', e.target.value || null)} className={`${inputClass} w-full`} />
          </Field>
        </div>
      </div>
      <div className="flex items-center gap-2">
        <div className="text-sm font-semibold text-slate-700">섹션·문항</div>
        <span className="text-xs text-slate-400">문항 {count}개</span>
        <Button size="xs" variant="text" className="ml-auto" onClick={renumber}>
          번호 다시 매기기
        </Button>
      </div>
      <div className={clsx('space-y-3')}>
        {p.sections.map((s, i) => (
          <SectionEditor
            key={i}
            section={s}
            index={i}
            nextNumber={nextNumber}
            onChange={(next) => set('sections', p.sections.map((x, j) => (j === i ? next : x)))}
            onRemove={() => set('sections', p.sections.filter((_, j) => j !== i))}
            onMove={(d) => set('sections', move(p.sections, i, d))}
          />
        ))}
      </div>
      <Button onClick={() => set('sections', [...p.sections, { title: null, body: null, items: [] }])}>+ 섹션</Button>
    </div>
  );
}
