import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { useState } from 'react';
import { api, errorMessage } from '../../api/client';
import { EMPTY_CONDITIONS, type Conditions, type ContextRule, type ContextRuleInput, type RulePreview } from '../../api/types';
import { Page } from '../../components/Layout';
import { Button, Card, Chip, ConfirmButton, Empty, ErrorBanner, Loading, Modal, PageHeader, Toggle, inputClass } from '../../components/ui';
import { relTime } from '../../lib/format';
import { useLabels, useMaterialOptions } from '../../lib/queries';
import { useToast } from '../../lib/toast';
import { ConditionFields } from '../studio/ConditionEditor';
import { describeClause, EMPTY_RULE, RuleEditor } from './RuleEditor';
import { RulePreviewView } from './RulePreviewView';

function toInput(r: ContextRule): ContextRuleInput {
  return {
    name: r.name,
    description: r.description,
    enabled: r.enabled,
    priority: r.priority,
    condition: r.condition,
    injection: r.injection,
  };
}

function PreviewTester() {
  const [conditions, setConditions] = useState<Conditions>(EMPTY_CONDITIONS);
  const [text, setText] = useState('');
  const [preview, setPreview] = useState<RulePreview | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const run = async () => {
    setLoading(true);
    setError(null);
    try {
      setPreview(await api.previewRules(conditions, text));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  };
  return (
    <Card className="p-4 space-y-3">
      <div>
        <div className="font-semibold text-slate-700">미리보기 테스터</div>
        <div className="text-xs text-slate-500">작업실 조건과 요청 문장을 넣고 어떤 규칙이 적용되어 무엇이 주입되는지 확인합니다.</div>
      </div>
      <ConditionFields value={conditions} onChange={setConditions} />
      <div className="flex gap-2">
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && !e.nativeEvent.isComposing && run()}
          placeholder="요청 문장 (예: 서술형 3문항으로 단원평가 만들어줘)"
          aria-label="요청 문장"
          className={`${inputClass} flex-1`}
        />
        <Button variant="primary" onClick={run} disabled={loading}>
          평가
        </Button>
      </div>
      <ErrorBanner message={error} />
      <RulePreviewView preview={preview} loading={loading} />
    </Card>
  );
}

export default function RulesPage() {
  const qc = useQueryClient();
  const toast = useToast();
  const { data: labels = [] } = useLabels();
  const { data: materials = [] } = useMaterialOptions();
  const query = useQuery({ queryKey: ['rules'], queryFn: api.rules });
  const [editing, setEditing] = useState<{ id: string | null; initial: ContextRuleInput } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const rules = query.data ?? [];

  const invalidate = () => qc.invalidateQueries({ queryKey: ['rules'] });
  const save = useMutation({
    mutationFn: ({ id, input }: { id: string | null; input: ContextRuleInput }) =>
      id ? api.updateRule(id, input) : api.createRule(input),
    onSuccess: () => {
      setEditing(null);
      toast('규칙을 저장했습니다');
      invalidate();
    },
    onError: (e) => setError(errorMessage(e)),
  });
  const toggle = useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) => api.updateRule(id, { enabled }),
    onSuccess: invalidate,
    onError: (e) => setError(errorMessage(e)),
  });
  const remove = useMutation({
    mutationFn: (id: string) => api.deleteRule(id),
    onSuccess: invalidate,
    onError: (e) => setError(errorMessage(e)),
  });

  return (
    <Page wide>
      <PageHeader
        title="컨텍스트 규칙"
        sub="요청의 조건(라벨·산출물 종류·문항 유형·대상·요청 문장)이 맞으면 지침·고정 자료·라벨 범위 검색 결과를 AI 요청에 함께 붙입니다."
        actions={
          <Button variant="primary" onClick={() => setEditing({ id: null, initial: EMPTY_RULE })}>
            + 규칙
          </Button>
        }
      />
      <ErrorBanner message={error ?? (query.error ? errorMessage(query.error) : null)} />
      <div className="grid xl:grid-cols-[1fr_520px] gap-4 items-start">
        <div className="space-y-2">
          {query.isLoading ? (
            <Loading />
          ) : rules.length === 0 ? (
            <Empty>규칙이 없습니다. 예: "과목:수학 + 서술형이면 채점기준 작성 지침 주입", "시험지면 학교 양식 자료 주입".</Empty>
          ) : (
            rules.map((r) => (
              <Card key={r.id} className={clsx('p-4 space-y-2', !r.enabled && 'opacity-60')}>
                <div className="flex items-center gap-2">
                  <Toggle checked={r.enabled} onChange={(v) => toggle.mutate({ id: r.id, enabled: v })} label={`${r.name} 사용`} />
                  <div className="font-semibold text-slate-800">{r.name}</div>
                  <Chip className="bg-slate-100 text-slate-500" title="우선순위">
                    P{r.priority}
                  </Chip>
                  <span className="ml-auto text-[11px] text-slate-400">{relTime(r.updatedAt)}</span>
                  <Button size="xs" variant="text" onClick={() => setEditing({ id: r.id, initial: toInput(r) })}>
                    편집
                  </Button>
                  <ConfirmButton label="삭제" onConfirm={() => remove.mutate(r.id)} />
                </div>
                {r.description && <div className="text-xs text-slate-500">{r.description}</div>}
                <div className="text-xs space-y-0.5">
                  <div className="text-slate-400">
                    조건 {r.condition.clauses.length === 0 ? '— 항상 적용' : `(${r.condition.match === 'ALL' ? '모두' : '하나라도'})`}
                  </div>
                  {r.condition.clauses.map((c, i) => (
                    <div key={i} className="text-slate-600 pl-2">
                      · {describeClause(c, labels, materials)}
                    </div>
                  ))}
                </div>
                <div className="flex flex-wrap gap-1">
                  {r.injection.instruction && <Chip className="bg-violet-50 text-violet-700">지침 {r.injection.instruction.length}자</Chip>}
                  {r.injection.materialIds.length > 0 && (
                    <Chip className="bg-violet-50 text-violet-700">자료 {r.injection.materialIds.length}개</Chip>
                  )}
                  {r.injection.retrieval && (
                    <Chip className="bg-violet-50 text-violet-700">
                      검색 top {r.injection.retrieval.topK}
                      {r.injection.retrieval.labelIds.length ? ` · 라벨 ${r.injection.retrieval.labelIds.length}` : ''}
                    </Chip>
                  )}
                  <Chip className="bg-slate-100 text-slate-500">최대 {r.injection.maxChars.toLocaleString()}자</Chip>
                </div>
              </Card>
            ))
          )}
        </div>
        <PreviewTester />
      </div>
      <Modal
        open={editing !== null}
        onClose={() => setEditing(null)}
        title={editing?.id ? '규칙 편집' : '새 규칙'}
        width="max-w-4xl"
      >
        {editing && (
          <RuleEditor
            key={editing.id ?? 'new'}
            initial={editing.initial}
            saving={save.isPending}
            onCancel={() => setEditing(null)}
            onSave={(input) => save.mutate({ id: editing.id, input })}
          />
        )}
      </Modal>
    </Page>
  );
}
