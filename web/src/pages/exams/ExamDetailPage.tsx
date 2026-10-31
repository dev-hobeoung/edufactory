import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api, errorMessage } from '../../api/client';
import type { ScoreSet, ScoreSetInput } from '../../api/types';
import { LabelList } from '../../components/LabelBadge';
import { Page } from '../../components/Layout';
import { Button, Card, ConfirmButton, ErrorBanner, Loading, Modal, inputClass } from '../../components/ui';
import { num } from '../../lib/format';
import { useToast } from '../../lib/toast';
import { ExamStatView } from './ExamStatView';
import { ItemStatsTable } from './ItemStatsTable';
import { ScoreGrid } from './ScoreGrid';

function ScoreSetForm({
  open,
  initial,
  onClose,
  onSubmit,
  busy,
  title,
}: {
  open: boolean;
  initial: ScoreSetInput;
  onClose: () => void;
  onSubmit: (v: ScoreSetInput) => void;
  busy: boolean;
  title: string;
}) {
  const [f, setF] = useState<ScoreSetInput>(initial);
  useEffect(() => {
    if (open) setF(initial);
  }, [open]);
  return (
    <Modal open={open} onClose={onClose} title={title} width="max-w-md">
      <div className="space-y-3">
        <label className="block space-y-1">
          <span className="text-xs font-medium text-slate-500">이름</span>
          <input
            autoFocus
            value={f.name}
            onChange={(e) => setF({ ...f, name: e.target.value })}
            placeholder="예: 2026 1학기 중간 · 2학년"
            className={`${inputClass} w-full`}
          />
        </label>
        <label className="block space-y-1">
          <span className="text-xs font-medium text-slate-500">시행일</span>
          <input
            type="date"
            value={f.takenOn ?? ''}
            onChange={(e) => setF({ ...f, takenOn: e.target.value || null })}
            className={`${inputClass} w-full`}
          />
        </label>
        <label className="block space-y-1">
          <span className="text-xs font-medium text-slate-500">메모</span>
          <input value={f.note ?? ''} onChange={(e) => setF({ ...f, note: e.target.value || null })} className={`${inputClass} w-full`} />
        </label>
        <div className="flex justify-end gap-2">
          <Button onClick={onClose}>취소</Button>
          <Button variant="primary" disabled={!f.name.trim() || busy} onClick={() => onSubmit({ ...f, name: f.name.trim() })}>
            저장
          </Button>
        </div>
      </div>
    </Modal>
  );
}

export default function ExamDetailPage() {
  const { materialId = '' } = useParams<{ materialId: string }>();
  const qc = useQueryClient();
  const toast = useToast();
  const query = useQuery({ queryKey: ['exam', materialId], queryFn: () => api.exam(materialId) });
  const [activeSet, setActiveSet] = useState<string | null>(null);
  const [form, setForm] = useState<{ mode: 'new' } | { mode: 'edit'; set: ScoreSet } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const exam = query.data;

  useEffect(() => {
    if (!exam) return;
    if (activeSet && exam.scoreSets.some((s) => s.id === activeSet)) return;
    setActiveSet(exam.scoreSets[0]?.id ?? null);
  }, [exam]);

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ['exam', materialId] });
    qc.invalidateQueries({ queryKey: ['exams'] });
  };

  const create = useMutation({
    mutationFn: (v: ScoreSetInput) => api.createScoreSet(materialId, v),
    onSuccess: (s) => {
      setForm(null);
      setActiveSet(s.id);
      toast('성적 세트를 만들었습니다');
      refresh();
    },
    onError: (e) => setError(errorMessage(e)),
  });
  const update = useMutation({
    mutationFn: ({ id, v }: { id: string; v: ScoreSetInput }) => api.updateScoreSet(id, v),
    onSuccess: () => {
      setForm(null);
      refresh();
    },
    onError: (e) => setError(errorMessage(e)),
  });
  const remove = useMutation({
    mutationFn: (id: string) => api.deleteScoreSet(id),
    onSuccess: () => {
      setActiveSet(null);
      refresh();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  if (query.isLoading) return <Loading />;
  if (!exam)
    return (
      <Page>
        <ErrorBanner message={query.error ? errorMessage(query.error) : '시험을 찾을 수 없습니다'} onRetry={() => query.refetch()} />
      </Page>
    );

  const set = exam.scoreSets.find((s) => s.id === activeSet) ?? null;
  const maxTotal = exam.items.reduce((a, i) => a + i.maxScore, 0);
  const missingAnswers = exam.items.filter((i) => !i.answer).length;

  return (
    <Page wide>
      <div>
        <Link to="/exams" className="text-xs text-slate-400 hover:text-slate-600">
          ← 시험 목록
        </Link>
        <div className="flex items-end gap-3 flex-wrap mt-0.5">
          <h1 className="text-2xl font-bold text-slate-800">{exam.material.title}</h1>
          <Link to={`/materials/${exam.material.id}`} className="text-xs text-indigo-600 hover:underline pb-1">
            문항 편집 (자료)
          </Link>
        </div>
        <div className="flex items-center gap-2 flex-wrap mt-1 text-xs text-slate-500">
          <span>
            문항 {exam.items.length} · 만점 {num(maxTotal, 1)}
          </span>
          <LabelList labels={exam.material.labels} />
        </div>
        {missingAnswers > 0 && (
          <div className="text-xs text-amber-700 mt-1">
            정답이 없는 문항이 {missingAnswers}개 있습니다. 응답 입력(자동 채점)을 쓰려면 자료 화면에서 정답을 넣으세요.
          </div>
        )}
      </div>
      <ErrorBanner message={error} />

      <Card className="p-4 space-y-3">
        <div className="font-semibold text-slate-700">전체 성적 (모든 세트 합산)</div>
        <ExamStatView stat={exam.stat} />
      </Card>

      <div className="space-y-2">
        <div className="font-semibold text-slate-700">문항별 통계</div>
        <ItemStatsTable items={exam.items} />
      </div>

      <Card className="p-4 space-y-3">
        <div className="flex items-center gap-2 flex-wrap">
          <div className="font-semibold text-slate-700 mr-2">성적 세트</div>
          {exam.scoreSets.map((s) => (
            <button
              key={s.id}
              type="button"
              onClick={() => setActiveSet(s.id)}
              className={clsx(
                'px-3 py-1 rounded-full text-xs border',
                s.id === activeSet ? 'bg-indigo-600 text-white border-indigo-600' : 'bg-white text-slate-600 border-slate-200 hover:border-indigo-300',
              )}
            >
              {s.name} · {s.studentCount}명
            </button>
          ))}
          <Button size="xs" variant="secondary" onClick={() => setForm({ mode: 'new' })}>
            + 세트
          </Button>
          {set && (
            <span className="ml-auto flex gap-1 items-center text-xs text-slate-400">
              {set.takenOn && <span>{set.takenOn}</span>}
              {set.note && <span className="truncate max-w-[200px]">{set.note}</span>}
              <Button size="xs" variant="text" onClick={() => setForm({ mode: 'edit', set })}>
                정보 수정
              </Button>
              <ConfirmButton label="세트 삭제" confirmLabel="성적까지 삭제" onConfirm={() => remove.mutate(set.id)} />
            </span>
          )}
        </div>
        {set ? (
          <ScoreGrid key={set.id} set={set} items={exam.items} onChanged={refresh} />
        ) : (
          <div className="text-sm text-slate-400 py-4">
            성적 세트가 없습니다. 응시 회차·집단마다 세트를 만들고 성적을 넣으세요 (학생은 익명 키로만 구분합니다).
          </div>
        )}
      </Card>

      <ScoreSetForm
        open={form !== null}
        title={form?.mode === 'edit' ? '성적 세트 수정' : '새 성적 세트'}
        initial={form?.mode === 'edit' ? { name: form.set.name, takenOn: form.set.takenOn, note: form.set.note } : { name: '', takenOn: null, note: null }}
        busy={create.isPending || update.isPending}
        onClose={() => setForm(null)}
        onSubmit={(v) => (form?.mode === 'edit' ? update.mutate({ id: form.set.id, v }) : create.mutate(v))}
      />
    </Page>
  );
}
