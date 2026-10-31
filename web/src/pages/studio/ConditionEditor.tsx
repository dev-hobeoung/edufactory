import { useEffect, useState } from 'react';
import { api, errorMessage } from '../../api/client';
import { EMPTY_CONDITIONS, type Conditions, type OutputKind, type RulePreview } from '../../api/types';
import { LabelPicker, MaterialPicker, MultiToggle } from '../../components/pickers';
import { Button, ErrorBanner, Modal, Slider, inputClass } from '../../components/ui';
import {
  CREATIVITY_HINTS,
  CREATIVITY_LEVELS,
  OUTPUT_KINDS,
  OUTPUT_KIND_LABEL,
  QUESTION_TYPES,
  QUESTION_TYPE_LABEL,
} from '../../lib/labels';
import { useLabels } from '../../lib/queries';
import { RulePreviewView } from '../rules/RulePreviewView';
import { conditionsLabel } from './TurnView';

const intOrNull = (s: string) => {
  const n = Number.parseInt(s, 10);
  return Number.isFinite(n) && n > 0 ? n : null;
};

/** 조건 편집 폼 — 작업실과 규칙 미리보기 테스터가 같이 쓴다 */
export function ConditionFields({ value, onChange }: { value: Conditions; onChange: (c: Conditions) => void }) {
  const set = <K extends keyof Conditions>(k: K, v: Conditions[K]) => onChange({ ...value, [k]: v });
  const rate = value.targetCorrectRate;
  return (
    <div className="grid grid-cols-6 gap-2.5">
      <label className="col-span-2 space-y-1">
        <span className="text-[11px] text-slate-500">만들 자료 종류</span>
        <select
          value={value.outputKind ?? ''}
          onChange={(e) => set('outputKind', (e.target.value || null) as OutputKind | null)}
          className={`${inputClass} w-full text-xs`}
        >
          <option value="">자동 (요청에 맞게)</option>
          {OUTPUT_KINDS.map((k) => (
            <option key={k} value={k}>
              {OUTPUT_KIND_LABEL[k]}
            </option>
          ))}
        </select>
      </label>
      <label className="col-span-2 space-y-1">
        <span className="text-[11px] text-slate-500">대상</span>
        <input
          value={value.audience ?? ''}
          onChange={(e) => set('audience', e.target.value || null)}
          placeholder="예: 고2 중위권"
          className={`${inputClass} w-full text-xs`}
        />
      </label>
      <div className="col-span-2 grid grid-cols-2 gap-2">
        <label className="space-y-1">
          <span className="text-[11px] text-slate-500">문항 수</span>
          <input
            type="number"
            min={1}
            value={value.itemCount ?? ''}
            onChange={(e) => set('itemCount', intOrNull(e.target.value))}
            className={`${inputClass} w-full text-xs`}
          />
        </label>
        <label className="space-y-1">
          <span className="text-[11px] text-slate-500">총점</span>
          <input
            type="number"
            min={1}
            value={value.totalScore ?? ''}
            onChange={(e) => set('totalScore', intOrNull(e.target.value))}
            className={`${inputClass} w-full text-xs`}
          />
        </label>
      </div>
      <div className="col-span-3 space-y-1">
        <span className="text-[11px] text-slate-500">참고 범위 라벨 (검색 기본 필터·규칙 조건)</span>
        <LabelPicker value={value.labelIds} onChange={(ids) => set('labelIds', ids)} placeholder="라벨 선택 (선택 안 하면 전체)" />
      </div>
      <div className="col-span-3 space-y-1">
        <span className="text-[11px] text-slate-500">꼭 참고할 자료</span>
        <MaterialPicker value={value.materialIds} onChange={(ids) => set('materialIds', ids)} placeholder="자료 선택 (선택)" />
      </div>
      <div className="col-span-6 space-y-1">
        <span className="text-[11px] text-slate-500">문항 유형</span>
        <MultiToggle
          label="문항 유형"
          value={value.questionTypes}
          options={QUESTION_TYPES.map((q) => ({ value: q, label: QUESTION_TYPE_LABEL[q] }))}
          onChange={(v) => set('questionTypes', v)}
        />
      </div>
      <div className="col-span-3 space-y-1">
        <div className="flex items-baseline justify-between">
          <span className="text-[11px] text-slate-500">목표 정답률</span>
          <label className="text-[11px] text-slate-500 flex items-center gap-1">
            <input
              type="checkbox"
              checked={rate !== null}
              onChange={(e) => set('targetCorrectRate', e.target.checked ? 0.6 : null)}
            />
            지정
          </label>
        </div>
        <div className="flex items-center gap-2">
          <input
            type="range"
            min={5}
            max={95}
            step={5}
            disabled={rate === null}
            value={Math.round((rate ?? 0.6) * 100)}
            onChange={(e) => set('targetCorrectRate', Number(e.target.value) / 100)}
            aria-label="목표 정답률"
            className="flex-1 accent-indigo-600 disabled:opacity-40"
          />
          <span className="text-xs font-semibold text-indigo-700 w-10 text-right tabular-nums">
            {rate === null ? '–' : `${Math.round(rate * 100)}%`}
          </span>
        </div>
      </div>
      <div className="col-span-3 space-y-1">
        <div className="flex items-baseline justify-between">
          <span className="text-[11px] text-slate-500">외부 자료(웹 검색) 참조</span>
          <span className="text-[11px] font-semibold text-indigo-700">
            {value.creativity ?? 0}단계 · {CREATIVITY_LEVELS[value.creativity ?? 0]}
          </span>
        </div>
        <div className="px-3">
          <Slider
            label="외부 자료 참조"
            steps={CREATIVITY_LEVELS}
            value={value.creativity ?? 0}
            onChange={(v) => set('creativity', v > 0 ? v : null)}
          />
        </div>
        <p className="text-[11px] text-slate-400">{CREATIVITY_HINTS[value.creativity ?? 0]}</p>
      </div>
      <label className="col-span-6 space-y-1">
        <span className="text-[11px] text-slate-500">기타 조건</span>
        <input
          value={value.extra ?? ''}
          onChange={(e) => set('extra', e.target.value || null)}
          placeholder="예: 그래프 해석 문항 포함, 계산량은 적게"
          className={`${inputClass} w-full text-xs`}
        />
      </label>
    </div>
  );
}

function CondChip({ label, value, onClick }: { label: string; value: string | null; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={
        value
          ? 'px-2 py-0.5 rounded-full text-xs font-semibold bg-white border border-slate-300 text-slate-700 hover:border-indigo-400 max-w-[260px] truncate'
          : 'px-2 py-0.5 rounded-full text-xs font-medium bg-slate-50 border border-dashed border-slate-300 text-slate-400 hover:text-slate-600'
      }
    >
      {label}
      {value ? ` · ${value}` : ''}
    </button>
  );
}

export function ConditionEditor({
  value,
  onChange,
  requestText,
}: {
  value: Conditions;
  onChange: (c: Conditions) => void;
  /** 미리보기에 쓸 지금 입력 중인 요청 */
  requestText: string;
}) {
  const { data: labels = [] } = useLabels();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<Conditions>(value);
  const [preview, setPreview] = useState<RulePreview | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [previewing, setPreviewing] = useState(false);

  useEffect(() => {
    if (!open) setForm(value);
  }, [value, open]);

  const runPreview = async (c: Conditions) => {
    setPreviewOpen(true);
    setPreviewing(true);
    setPreviewError(null);
    try {
      setPreview(await api.previewRules(c, requestText));
    } catch (e) {
      setPreviewError(errorMessage(e));
    } finally {
      setPreviewing(false);
    }
  };

  const labelText = conditionsLabel({ ...EMPTY_CONDITIONS, labelIds: value.labelIds }, labels)[0] ?? null;
  const show = () => setOpen(true);

  return (
    <div className="space-y-2">
      <div className="flex flex-wrap gap-1.5 items-center">
        <CondChip label="종류" value={value.outputKind ? OUTPUT_KIND_LABEL[value.outputKind] : null} onClick={show} />
        <CondChip label="라벨" value={labelText} onClick={show} />
        <CondChip label="자료" value={value.materialIds.length ? `${value.materialIds.length}개` : null} onClick={show} />
        <CondChip
          label="유형"
          value={value.questionTypes.length ? value.questionTypes.map((q) => QUESTION_TYPE_LABEL[q]).join('·') : null}
          onClick={show}
        />
        <CondChip label="문항 수" value={value.itemCount !== null ? `${value.itemCount}개` : null} onClick={show} />
        <CondChip
          label="정답률"
          value={value.targetCorrectRate !== null ? `${Math.round(value.targetCorrectRate * 100)}%` : null}
          onClick={show}
        />
        <CondChip label="대상" value={value.audience} onClick={show} />
        {(value.totalScore !== null || value.creativity || value.extra) && (
          <CondChip
            label="기타"
            value={[
              value.totalScore !== null ? `총점 ${value.totalScore}` : null,
              value.creativity ? `외부 ${CREATIVITY_LEVELS[value.creativity]}` : null,
              value.extra,
            ]
              .filter(Boolean)
              .join(' · ')}
            onClick={show}
          />
        )}
        <button
          type="button"
          onClick={() => setOpen((v) => !v)}
          className="text-xs text-slate-400 hover:text-indigo-600 px-1"
          aria-expanded={open}
        >
          {open ? '접기' : '+ 조건'}
        </button>
        <button
          type="button"
          onClick={() => runPreview(open ? form : value)}
          className="text-xs text-violet-600 hover:text-violet-800 px-1 ml-auto"
        >
          주입될 컨텍스트 미리보기
        </button>
      </div>
      {open && (
        <div className="border border-slate-200 rounded-xl p-3 bg-slate-50 space-y-2.5 max-h-[55vh] overflow-y-auto">
          <ConditionFields value={form} onChange={setForm} />
          <div className="flex justify-between items-center">
            <span className="text-[11px] text-slate-400">조건은 대화 내내 유지되며 메시지마다 함께 전달됩니다.</span>
            <div className="flex gap-1.5">
              <Button
                size="xs"
                variant="text"
                onClick={() => {
                  onChange(EMPTY_CONDITIONS);
                  setOpen(false);
                }}
              >
                모두 지우기
              </Button>
              <Button size="xs" variant="secondary" onClick={() => setOpen(false)}>
                취소
              </Button>
              <Button
                size="xs"
                variant="primary"
                onClick={() => {
                  onChange(form);
                  setOpen(false);
                }}
              >
                적용
              </Button>
            </div>
          </div>
        </div>
      )}
      <Modal open={previewOpen} onClose={() => setPreviewOpen(false)} title="주입될 컨텍스트 미리보기" width="max-w-3xl">
        <div className="space-y-3">
          <p className="text-xs text-slate-500">
            지금 조건{requestText.trim() ? '과 입력 중인 요청' : ''}으로 보내면 아래 규칙의 컨텍스트가 요청에 함께 붙습니다.
          </p>
          <ErrorBanner message={previewError} />
          <RulePreviewView preview={preview} loading={previewing} />
        </div>
      </Modal>
    </div>
  );
}
