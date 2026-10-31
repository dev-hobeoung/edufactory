import { useState } from 'react';
import type { ChunkType, ContextRuleInput, Label, MaterialSummary, OutputKind, QuestionType, RuleClause, RuleField, RuleOp } from '../../api/types';
import { LabelPicker, MaterialPicker, MultiToggle, TagInput } from '../../components/pickers';
import { Button, Field, Segmented, Toggle, inputClass, textareaClass } from '../../components/ui';
import {
  CHUNK_TYPES,
  CHUNK_TYPE_LABEL,
  OUTPUT_KINDS,
  OUTPUT_KIND_LABEL,
  QUESTION_TYPES,
  QUESTION_TYPE_LABEL,
  RULE_FIELDS,
  RULE_FIELD_LABEL,
  RULE_OP_LABEL,
  TEXT_FIELDS,
} from '../../lib/labels';

export const EMPTY_RULE: ContextRuleInput = {
  name: '',
  description: null,
  enabled: true,
  priority: 0,
  condition: { match: 'ALL', clauses: [] },
  injection: { instruction: null, materialIds: [], retrieval: null, maxChars: 8000 },
};

const TEXT_OP_LABEL: Record<RuleOp, string> = { any: '하나라도 들어 있음', all: '모두 들어 있음', none: '하나도 없음' };

/** 조건 한 줄을 사람이 읽는 문장으로 */
export function describeClause(c: RuleClause, labels: Label[], materials: MaterialSummary[]): string {
  const names = c.values.map((v) => {
    switch (c.field) {
      case 'labels': {
        const l = labels.find((x) => x.id === v);
        return l ? `${l.group}:${l.name}` : '(삭제된 라벨)';
      }
      case 'materials':
        return materials.find((m) => m.id === v)?.title ?? '(삭제된 자료)';
      case 'outputKind':
        return OUTPUT_KIND_LABEL[v as OutputKind] ?? v;
      case 'questionTypes':
        return QUESTION_TYPE_LABEL[v as QuestionType] ?? v;
      default:
        return `"${v}"`;
    }
  });
  const opLabel = TEXT_FIELDS.includes(c.field) ? TEXT_OP_LABEL[c.op] : RULE_OP_LABEL[c.op];
  return `${RULE_FIELD_LABEL[c.field]}: ${names.join(', ') || '(값 없음)'} — ${opLabel}`;
}

function ClauseValues({ clause, onChange }: { clause: RuleClause; onChange: (values: string[]) => void }) {
  switch (clause.field) {
    case 'labels':
      return <LabelPicker value={clause.values} onChange={onChange} />;
    case 'materials':
      return <MaterialPicker value={clause.values} onChange={onChange} />;
    case 'outputKind':
      return (
        <MultiToggle
          label="산출물 종류"
          value={clause.values as OutputKind[]}
          options={OUTPUT_KINDS.map((k) => ({ value: k, label: OUTPUT_KIND_LABEL[k] }))}
          onChange={onChange}
        />
      );
    case 'questionTypes':
      return (
        <MultiToggle
          label="문항 유형"
          value={clause.values as QuestionType[]}
          options={QUESTION_TYPES.map((k) => ({ value: k, label: QUESTION_TYPE_LABEL[k] }))}
          onChange={onChange}
        />
      );
    default:
      return (
        <TagInput
          value={clause.values}
          onChange={onChange}
          placeholder={clause.field === 'request' ? '요청 문장에 들어갈 낱말 (Enter)' : '대상에 들어갈 낱말 (Enter)'}
        />
      );
  }
}

export function RuleEditor({
  initial,
  onSave,
  onCancel,
  saving,
}: {
  initial: ContextRuleInput;
  onSave: (r: ContextRuleInput) => void;
  onCancel: () => void;
  saving: boolean;
}) {
  const [r, setR] = useState<ContextRuleInput>(initial);
  const cond = r.condition;
  const inj = r.injection;
  const setClause = (i: number, c: RuleClause) =>
    setR({ ...r, condition: { ...cond, clauses: cond.clauses.map((x, j) => (j === i ? c : x)) } });
  const setInj = (patch: Partial<typeof inj>) => setR({ ...r, injection: { ...inj, ...patch } });

  const emptyInjection = !inj.instruction?.trim() && inj.materialIds.length === 0 && !inj.retrieval;

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-6 gap-3">
        <div className="col-span-3">
          <Field label="이름">
            <input value={r.name} onChange={(e) => setR({ ...r, name: e.target.value })} className={`${inputClass} w-full`} />
          </Field>
        </div>
        <div className="col-span-1">
          <Field label="우선순위" hint="높을수록 먼저">
            <input
              type="number"
              value={r.priority}
              onChange={(e) => setR({ ...r, priority: Number(e.target.value) || 0 })}
              className={`${inputClass} w-full`}
            />
          </Field>
        </div>
        <div className="col-span-2 flex items-center gap-2 pt-4">
          <Toggle checked={r.enabled} onChange={(v) => setR({ ...r, enabled: v })} label="사용" />
          <span className="text-sm text-slate-600">{r.enabled ? '사용 중' : '꺼짐'}</span>
        </div>
        <div className="col-span-6">
          <Field label="설명 (선택)">
            <input
              value={r.description ?? ''}
              onChange={(e) => setR({ ...r, description: e.target.value || null })}
              className={`${inputClass} w-full`}
            />
          </Field>
        </div>
      </div>

      <section className="space-y-2 border border-slate-200 rounded-lg p-3">
        <div className="flex items-center gap-2">
          <div className="font-semibold text-sm text-slate-700">언제 — 조건</div>
          <Segmented
            label="조건 결합"
            value={cond.match}
            onChange={(m) => setR({ ...r, condition: { ...cond, match: m } })}
            options={[
              { value: 'ALL', label: '모두 만족' },
              { value: 'ANY', label: '하나라도 만족' },
            ]}
          />
          <Button
            size="xs"
            className="ml-auto"
            onClick={() => setR({ ...r, condition: { ...cond, clauses: [...cond.clauses, { field: 'labels', op: 'any', values: [] }] } })}
          >
            + 조건
          </Button>
        </div>
        {cond.clauses.length === 0 && <div className="text-xs text-slate-500">조건이 없으면 모든 요청에 항상 적용됩니다.</div>}
        {cond.clauses.map((c, i) => (
          <div key={i} className="flex gap-2 items-start bg-slate-50 rounded-lg p-2">
            <select
              value={c.field}
              onChange={(e) => setClause(i, { field: e.target.value as RuleField, op: 'any', values: [] })}
              aria-label="조건 항목"
              className={`${inputClass} text-xs w-32`}
            >
              {RULE_FIELDS.map((f) => (
                <option key={f} value={f}>
                  {RULE_FIELD_LABEL[f]}
                </option>
              ))}
            </select>
            <select
              value={c.op}
              onChange={(e) => setClause(i, { ...c, op: e.target.value as RuleOp })}
              aria-label="비교 방식"
              className={`${inputClass} text-xs w-36`}
            >
              {(['any', 'all', 'none'] as RuleOp[]).map((op) => (
                <option key={op} value={op}>
                  {TEXT_FIELDS.includes(c.field) ? TEXT_OP_LABEL[op] : RULE_OP_LABEL[op]}
                </option>
              ))}
            </select>
            <div className="flex-1 min-w-0">
              <ClauseValues clause={c} onChange={(values) => setClause(i, { ...c, values })} />
            </div>
            <Button
              size="xs"
              variant="text"
              onClick={() => setR({ ...r, condition: { ...cond, clauses: cond.clauses.filter((_, j) => j !== i) } })}
            >
              삭제
            </Button>
          </div>
        ))}
      </section>

      <section className="space-y-3 border border-slate-200 rounded-lg p-3">
        <div className="font-semibold text-sm text-slate-700">무엇을 — 주입할 컨텍스트</div>
        <Field label="지침 (마크다운)" hint="예: 서술형 채점기준은 부분점수 단계로, 계산 과정 점수를 포함한다.">
          <textarea
            value={inj.instruction ?? ''}
            onChange={(e) => setInj({ instruction: e.target.value || null })}
            rows={5}
            className={`${textareaClass} text-sm`}
          />
        </Field>
        <div className="space-y-1">
          <span className="text-xs font-medium text-slate-500">자료 본문 그대로 주입 (양식 예시, 성취기준 문서 등)</span>
          <MaterialPicker value={inj.materialIds} onChange={(ids) => setInj({ materialIds: ids })} />
        </div>
        <div className="space-y-2 bg-slate-50 rounded-lg p-2.5">
          <div className="flex items-center gap-2">
            <Toggle
              checked={!!inj.retrieval}
              onChange={(v) => setInj({ retrieval: v ? { query: null, labelIds: [], chunkTypes: [], topK: 5 } : null })}
              label="라벨 범위 검색 결과 주입"
            />
            <span className="text-sm text-slate-700">라벨 범위 검색 결과 주입</span>
          </div>
          {inj.retrieval && (
            <div className="grid grid-cols-6 gap-2">
              <div className="col-span-4">
                <Field label="검색어" hint="비우면 사용자 요청 문장으로 검색합니다">
                  <input
                    value={inj.retrieval.query ?? ''}
                    onChange={(e) => setInj({ retrieval: { ...inj.retrieval!, query: e.target.value || null } })}
                    className={`${inputClass} w-full text-xs`}
                  />
                </Field>
              </div>
              <div className="col-span-2">
                <Field label="개수 (topK)">
                  <input
                    type="number"
                    min={1}
                    max={30}
                    value={inj.retrieval.topK}
                    onChange={(e) => setInj({ retrieval: { ...inj.retrieval!, topK: Math.max(1, Math.min(30, Number(e.target.value) || 5)) } })}
                    className={`${inputClass} w-full text-xs`}
                  />
                </Field>
              </div>
              <div className="col-span-6 space-y-1">
                <span className="text-[11px] text-slate-500">검색 범위 라벨</span>
                <LabelPicker value={inj.retrieval.labelIds} onChange={(ids) => setInj({ retrieval: { ...inj.retrieval!, labelIds: ids } })} />
              </div>
              <div className="col-span-6 space-y-1">
                <span className="text-[11px] text-slate-500">청크 종류 (선택 안 하면 전체)</span>
                <MultiToggle
                  label="청크 종류"
                  value={inj.retrieval.chunkTypes}
                  options={CHUNK_TYPES.map((t) => ({ value: t, label: CHUNK_TYPE_LABEL[t] }))}
                  onChange={(v: ChunkType[]) => setInj({ retrieval: { ...inj.retrieval!, chunkTypes: v } })}
                />
              </div>
            </div>
          )}
        </div>
        <div className="w-48">
          <Field label="최대 글자 수" hint="이 규칙이 주입할 상한">
            <input
              type="number"
              min={500}
              step={500}
              value={inj.maxChars}
              onChange={(e) => setInj({ maxChars: Math.max(500, Number(e.target.value) || 8000) })}
              className={`${inputClass} w-full`}
            />
          </Field>
        </div>
        {emptyInjection && <div className="text-xs text-amber-700">주입할 내용이 없습니다. 지침·자료·검색 중 하나 이상을 넣으세요.</div>}
      </section>

      <div className="flex justify-end gap-2">
        <Button onClick={onCancel}>취소</Button>
        <Button variant="primary" disabled={!r.name.trim() || emptyInjection || saving} onClick={() => onSave({ ...r, name: r.name.trim() })}>
          {saving ? '저장 중…' : '저장'}
        </Button>
      </div>
    </div>
  );
}
