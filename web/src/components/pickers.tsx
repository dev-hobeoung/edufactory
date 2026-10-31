import clsx from 'clsx';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { Label } from '../api/types';
import { groupLabels, useLabels, useMaterialOptions } from '../lib/queries';
import { MATERIAL_KIND_LABEL } from '../lib/labels';
import { LabelBadge } from './LabelBadge';
import { inputClass } from './ui';

/** 바깥을 누르면 닫히는 드롭다운 */
function useOutside(open: boolean, close: () => void) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) close();
    };
    window.addEventListener('mousedown', onDown);
    return () => window.removeEventListener('mousedown', onDown);
  }, [open, close]);
  return ref;
}

/** 라벨 여러 개 고르기 — 선택한 라벨은 칩으로, 목록은 그룹별 */
export function LabelPicker({
  value,
  onChange,
  placeholder = '라벨 선택',
  aiIds = [],
}: {
  value: string[];
  onChange: (ids: string[]) => void;
  placeholder?: string;
  /** AI 가 붙인 라벨 id (점선 표시) */
  aiIds?: string[];
}) {
  const { data: labels = [] } = useLabels();
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState('');
  const ref = useOutside(open, () => setOpen(false));
  const selected = value.map((id) => labels.find((l) => l.id === id)).filter((l): l is Label => !!l);
  const groups = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const list = needle
      ? labels.filter((l) => `${l.group} ${l.name}`.toLowerCase().includes(needle))
      : labels;
    return groupLabels(list);
  }, [labels, q]);

  const toggle = (id: string) => onChange(value.includes(id) ? value.filter((x) => x !== id) : [...value, id]);

  return (
    <div ref={ref} className="relative">
      <div
        className={clsx(inputClass, 'min-h-[34px] flex flex-wrap gap-1 items-center cursor-pointer py-1')}
        onClick={() => setOpen(true)}
      >
        {selected.length === 0 && <span className="text-slate-400 text-sm">{placeholder}</span>}
        {selected.map((l) => (
          <LabelBadge key={l.id} label={l} ai={aiIds.includes(l.id)} onRemove={() => toggle(l.id)} />
        ))}
      </div>
      {open && (
        <div className="absolute z-30 mt-1 w-full min-w-[260px] bg-white border border-slate-200 rounded-lg shadow-lg p-2 space-y-2 max-h-72 overflow-y-auto">
          <input
            autoFocus
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="라벨 검색"
            aria-label="라벨 검색"
            className={`${inputClass} w-full text-xs`}
          />
          {groups.length === 0 && <div className="text-xs text-slate-400 px-1 py-2">라벨이 없습니다. 라벨 화면에서 먼저 만드세요.</div>}
          {groups.map(([group, list]) => (
            <div key={group}>
              <div className="text-[11px] text-slate-400 px-1 mb-1">{group}</div>
              <div className="flex flex-wrap gap-1">
                {list.map((l) => (
                  <button
                    key={l.id}
                    type="button"
                    onClick={() => toggle(l.id)}
                    className={clsx(
                      'px-2 py-0.5 rounded-full text-xs border',
                      value.includes(l.id)
                        ? 'bg-indigo-600 text-white border-indigo-600'
                        : 'bg-white text-slate-600 border-slate-200 hover:border-indigo-300',
                    )}
                  >
                    {l.name}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/** 자료 여러 개 고르기 */
export function MaterialPicker({
  value,
  onChange,
  placeholder = '자료 선택',
  examOnly = false,
}: {
  value: string[];
  onChange: (ids: string[]) => void;
  placeholder?: string;
  examOnly?: boolean;
}) {
  const { data: materials = [] } = useMaterialOptions();
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState('');
  const ref = useOutside(open, () => setOpen(false));
  const list = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return materials
      .filter((m) => !examOnly || m.isExam)
      .filter((m) => !needle || m.title.toLowerCase().includes(needle));
  }, [materials, q, examOnly]);
  const toggle = (id: string) => onChange(value.includes(id) ? value.filter((x) => x !== id) : [...value, id]);

  return (
    <div ref={ref} className="relative">
      <div
        className={clsx(inputClass, 'min-h-[34px] flex flex-wrap gap-1 items-center cursor-pointer py-1')}
        onClick={() => setOpen(true)}
      >
        {value.length === 0 && <span className="text-slate-400 text-sm">{placeholder}</span>}
        {value.map((id) => {
          const m = materials.find((x) => x.id === id);
          return (
            <span key={id} className="inline-flex items-center gap-1 bg-slate-100 text-slate-700 rounded-full px-2 py-0.5 text-xs">
              {m?.title ?? '(삭제된 자료)'}
              <button type="button" onClick={() => toggle(id)} aria-label="자료 빼기" className="hover:text-red-600">
                ×
              </button>
            </span>
          );
        })}
      </div>
      {open && (
        <div className="absolute z-30 mt-1 w-full min-w-[280px] bg-white border border-slate-200 rounded-lg shadow-lg p-2 space-y-1 max-h-72 overflow-y-auto">
          <input
            autoFocus
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="자료 검색"
            aria-label="자료 검색"
            className={`${inputClass} w-full text-xs mb-1`}
          />
          {list.length === 0 && <div className="text-xs text-slate-400 px-1 py-2">자료가 없습니다</div>}
          {list.map((m) => (
            <label key={m.id} className="flex items-center gap-2 px-1.5 py-1 rounded hover:bg-slate-50 text-sm cursor-pointer">
              <input type="checkbox" checked={value.includes(m.id)} onChange={() => toggle(m.id)} />
              <span className="flex-1 truncate">{m.title}</span>
              <span className="text-[11px] text-slate-400">{MATERIAL_KIND_LABEL[m.kind]}</span>
            </label>
          ))}
        </div>
      )}
    </div>
  );
}

/** 열거값 여러 개 토글 (문항 유형, 산출물 종류 등) */
export function MultiToggle<T extends string>({
  value,
  options,
  onChange,
  label,
}: {
  value: T[];
  options: { value: T; label: string }[];
  onChange: (v: T[]) => void;
  label: string;
}) {
  return (
    <div role="group" aria-label={label} className="flex flex-wrap gap-1">
      {options.map((o) => {
        const on = value.includes(o.value);
        return (
          <button
            key={o.value}
            type="button"
            aria-pressed={on}
            onClick={() => onChange(on ? value.filter((x) => x !== o.value) : [...value, o.value])}
            className={clsx(
              'px-2 py-0.5 rounded-full text-xs border',
              on ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white text-slate-600 border-slate-200 hover:border-indigo-300',
            )}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}

/** 자유 문자열 태그 입력 (Enter·쉼표로 추가) */
export function TagInput({
  value,
  onChange,
  placeholder = '입력 후 Enter',
}: {
  value: string[];
  onChange: (v: string[]) => void;
  placeholder?: string;
}) {
  const [text, setText] = useState('');
  const commit = () => {
    const parts = text
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
      .filter((s) => !value.includes(s));
    if (parts.length) onChange([...value, ...parts]);
    setText('');
  };
  return (
    <div className={clsx(inputClass, 'flex flex-wrap gap-1 items-center py-1 min-h-[34px]')}>
      {value.map((t) => (
        <span key={t} className="inline-flex items-center gap-1 bg-slate-100 text-slate-700 rounded-full px-2 py-0.5 text-xs">
          {t}
          <button type="button" onClick={() => onChange(value.filter((x) => x !== t))} aria-label={`${t} 빼기`}>
            ×
          </button>
        </span>
      ))}
      <input
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          if ((e.key === 'Enter' || e.key === ',') && !e.nativeEvent.isComposing) {
            e.preventDefault();
            commit();
          } else if (e.key === 'Backspace' && !text && value.length) {
            onChange(value.slice(0, -1));
          }
        }}
        onBlur={commit}
        placeholder={value.length ? '' : placeholder}
        className="flex-1 min-w-[80px] outline-none text-sm bg-transparent"
      />
    </div>
  );
}
