import { useMutation, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { useMemo, useState } from 'react';
import { api, errorMessage } from '../api/client';
import type { Label, LabelInput } from '../api/types';
import { LabelBadge } from '../components/LabelBadge';
import { Page } from '../components/Layout';
import { Button, Card, ConfirmButton, Empty, ErrorBanner, Loading, PageHeader, inputClass } from '../components/ui';
import { DEFAULT_LABEL_COLOR, LABEL_COLORS } from '../lib/labels';
import { groupLabels, useLabels } from '../lib/queries';
import { useToast } from '../lib/toast';

function ColorPicker({ value, onChange }: { value: string; onChange: (c: string) => void }) {
  return (
    <div className="flex gap-1" role="radiogroup" aria-label="색">
      {LABEL_COLORS.map((c) => (
        <button
          key={c}
          type="button"
          role="radio"
          aria-checked={value === c}
          aria-label={c}
          onClick={() => onChange(c)}
          className={clsx('w-5 h-5 rounded-full border-2', value === c ? 'border-slate-800' : 'border-transparent')}
          style={{ backgroundColor: c }}
        />
      ))}
    </div>
  );
}

function LabelForm({
  initial,
  groups,
  onSubmit,
  onCancel,
  busy,
  submitLabel,
}: {
  initial: LabelInput;
  groups: string[];
  onSubmit: (v: LabelInput) => void;
  onCancel?: () => void;
  busy: boolean;
  submitLabel: string;
}) {
  const [f, setF] = useState<LabelInput>(initial);
  const valid = f.group.trim() && f.name.trim();
  return (
    <div className="flex flex-wrap gap-2 items-center">
      <input
        list="label-groups"
        value={f.group}
        onChange={(e) => setF({ ...f, group: e.target.value })}
        placeholder="그룹 (예: 과목)"
        aria-label="그룹"
        className={`${inputClass} w-36`}
      />
      <datalist id="label-groups">
        {groups.map((g) => (
          <option key={g} value={g} />
        ))}
      </datalist>
      <input
        value={f.name}
        onChange={(e) => setF({ ...f, name: e.target.value })}
        placeholder="이름 (예: 수학)"
        aria-label="이름"
        className={`${inputClass} w-36`}
      />
      <input
        value={f.description ?? ''}
        onChange={(e) => setF({ ...f, description: e.target.value || null })}
        placeholder="설명 (선택)"
        aria-label="설명"
        className={`${inputClass} flex-1 min-w-[160px]`}
      />
      <ColorPicker value={f.color ?? DEFAULT_LABEL_COLOR} onChange={(c) => setF({ ...f, color: c })} />
      {onCancel && <Button onClick={onCancel}>취소</Button>}
      <Button
        variant="primary"
        disabled={!valid || busy}
        onClick={() => onSubmit({ ...f, group: f.group.trim(), name: f.name.trim() })}
      >
        {submitLabel}
      </Button>
    </div>
  );
}

export default function LabelsPage() {
  const qc = useQueryClient();
  const toast = useToast();
  const { data: labels = [], isLoading, error: loadError, refetch } = useLabels();
  const [editing, setEditing] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formKey, setFormKey] = useState(0);
  const grouped = useMemo(() => groupLabels(labels), [labels]);
  const groups = grouped.map(([g]) => g);

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['labels'] });
    qc.invalidateQueries({ queryKey: ['materials'] });
  };
  const create = useMutation({
    mutationFn: (v: LabelInput) => api.createLabel(v),
    onSuccess: (l) => {
      toast(`라벨 "${l.group} · ${l.name}"을 만들었습니다`);
      setFormKey((k) => k + 1);
      setError(null);
      invalidate();
    },
    onError: (e) => setError(errorMessage(e)),
  });
  const update = useMutation({
    mutationFn: ({ id, v }: { id: string; v: LabelInput }) => api.updateLabel(id, v),
    onSuccess: () => {
      setEditing(null);
      setError(null);
      invalidate();
    },
    onError: (e) => setError(errorMessage(e)),
  });
  const remove = useMutation({
    mutationFn: (id: string) => api.deleteLabel(id),
    onSuccess: invalidate,
    onError: (e) => setError(errorMessage(e)),
  });

  const row = (l: Label) =>
    editing === l.id ? (
      <div key={l.id} className="py-2">
        <LabelForm
          initial={{ group: l.group, name: l.name, color: l.color, description: l.description }}
          groups={groups}
          busy={update.isPending}
          submitLabel="저장"
          onCancel={() => setEditing(null)}
          onSubmit={(v) => update.mutate({ id: l.id, v })}
        />
      </div>
    ) : (
      <div key={l.id} className="flex items-center gap-3 py-2 group">
        <LabelBadge label={l} />
        <span className="text-xs text-slate-500 flex-1 truncate">{l.description}</span>
        <span className="text-xs text-slate-400 tabular-nums">
          자료 {l.materialCount} · 청크 {l.chunkCount}
        </span>
        <span className="flex gap-1 opacity-60 group-hover:opacity-100">
          <Button size="xs" variant="text" onClick={() => setEditing(l.id)}>
            편집
          </Button>
          <ConfirmButton label="삭제" confirmLabel="삭제 (규칙 조건에서도 빠짐)" onConfirm={() => remove.mutate(l.id)} />
        </span>
      </div>
    );

  return (
    <Page>
      <PageHeader
        title="라벨"
        sub="라벨은 '그룹 · 이름' 쌍입니다 (예: 과목 · 수학, 학년 · 고2, 단원 · 미분, 출처 · 교과서). 자료·청크에 붙여 검색 범위와 컨텍스트 규칙 조건으로 씁니다."
      />
      <Card className="p-4 space-y-2">
        <div className="text-sm font-semibold text-slate-700">새 라벨</div>
        <LabelForm
          key={formKey}
          initial={{ group: '', name: '', color: DEFAULT_LABEL_COLOR, description: null }}
          groups={groups}
          busy={create.isPending}
          submitLabel="추가"
          onSubmit={(v) => create.mutate(v)}
        />
      </Card>
      <ErrorBanner message={error ?? (loadError ? errorMessage(loadError) : null)} onRetry={loadError ? () => refetch() : undefined} />
      {isLoading ? (
        <Loading />
      ) : grouped.length === 0 ? (
        <Empty>아직 라벨이 없습니다.</Empty>
      ) : (
        <div className="grid gap-3 md:grid-cols-2">
          {grouped.map(([group, list]) => (
            <Card key={group} className="p-4">
              <div className="flex items-baseline justify-between mb-1">
                <div className="font-semibold text-slate-800">{group}</div>
                <div className="text-xs text-slate-400">{list.length}개</div>
              </div>
              <div className="divide-y divide-slate-100">{list.map(row)}</div>
            </Card>
          ))}
        </div>
      )}
    </Page>
  );
}
