import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, errorMessage } from '../../api/client';
import type { ChunkInput, ChunkType, MaterialDetail, MaterialKind } from '../../api/types';
import { LabelList } from '../../components/LabelBadge';
import { Page } from '../../components/Layout';
import { LabelPicker } from '../../components/pickers';
import { Button, Card, Chip, ConfirmButton, Empty, ErrorBanner, Field, Loading, Toggle, inputClass } from '../../components/ui';
import { bytes, dateTime } from '../../lib/format';
import { CHUNK_TYPES, CHUNK_TYPE_LABEL, MATERIAL_KINDS, MATERIAL_KIND_LABEL, PROCESSING_STATUSES } from '../../lib/labels';
import { useToast } from '../../lib/toast';
import { ChunkCard } from './ChunkCard';
import { ChunkEditor, chunkToInput, emptyChunkInput } from './ChunkEditor';
import { StatusBadge } from './MaterialsPage';

function MetaEditor({ m, onSaved }: { m: MaterialDetail; onSaved: () => void }) {
  const toast = useToast();
  const [title, setTitle] = useState(m.title);
  const [kind, setKind] = useState<MaterialKind>(m.kind);
  const [description, setDescription] = useState(m.description ?? '');
  const [labelIds, setLabelIds] = useState(m.labels.map((l) => l.id));
  const [isExam, setIsExam] = useState(m.isExam);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setTitle(m.title);
    setKind(m.kind);
    setDescription(m.description ?? '');
    setLabelIds(m.labels.map((l) => l.id));
    setIsExam(m.isExam);
  }, [m.id, m.updatedAt]);

  const dirty =
    title !== m.title ||
    kind !== m.kind ||
    description !== (m.description ?? '') ||
    isExam !== m.isExam ||
    JSON.stringify([...labelIds].sort()) !== JSON.stringify(m.labels.map((l) => l.id).sort());

  const save = useMutation({
    mutationFn: () =>
      api.updateMaterial(m.id, { title: title.trim(), kind, description: description.trim() || null, labelIds, isExam }),
    onSuccess: () => {
      toast('저장했습니다');
      setError(null);
      onSaved();
    },
    onError: (e) => setError(errorMessage(e)),
  });

  return (
    <Card className="p-4 space-y-3">
      <div className="grid grid-cols-3 gap-3">
        <Field label="제목">
          <input value={title} onChange={(e) => setTitle(e.target.value)} className={`${inputClass} w-full`} />
        </Field>
        <Field label="종류">
          <select value={kind} onChange={(e) => setKind(e.target.value as MaterialKind)} className={`${inputClass} w-full`}>
            {MATERIAL_KINDS.map((k) => (
              <option key={k} value={k}>
                {MATERIAL_KIND_LABEL[k]}
              </option>
            ))}
          </select>
        </Field>
        <Field label="설명">
          <input value={description} onChange={(e) => setDescription(e.target.value)} className={`${inputClass} w-full`} />
        </Field>
      </div>
      <div className="grid grid-cols-3 gap-3 items-end">
        <div className="col-span-2 space-y-1">
          <span className="text-xs font-medium text-slate-500">자료 라벨 (바꾸면 모든 청크를 다시 임베딩합니다)</span>
          <LabelPicker value={labelIds} onChange={setLabelIds} />
        </div>
        <div className="flex items-center gap-2 pb-1.5">
          <Toggle checked={isExam} onChange={setIsExam} label="시험 자료" />
          <span className="text-sm text-slate-700">시험 자료 (성적 입력)</span>
        </div>
      </div>
      <ErrorBanner message={error} />
      <div className="flex justify-end">
        <Button variant="primary" disabled={!dirty || !title.trim() || save.isPending} onClick={() => save.mutate()}>
          {save.isPending ? '저장 중…' : '정보 저장'}
        </Button>
      </div>
    </Card>
  );
}

export default function MaterialDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const toast = useToast();
  const [editing, setEditing] = useState<string | 'new' | null>(null);
  const [typeFilter, setTypeFilter] = useState<ChunkType | ''>('');
  const [error, setError] = useState<string | null>(null);

  const query = useQuery({
    queryKey: ['material', id],
    queryFn: () => api.material(id),
    refetchInterval: (q) => (q.state.data && PROCESSING_STATUSES.includes(q.state.data.status) ? 2500 : false),
  });
  const m = query.data;
  const processing = !!m && PROCESSING_STATUSES.includes(m.status);

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['material', id] });
    qc.invalidateQueries({ queryKey: ['materials'] });
  };

  const process = useMutation({
    mutationFn: (restructure: boolean) => api.processMaterial(id, restructure),
    onSuccess: (_, restructure) => {
      toast(restructure ? '구조화를 다시 시작했습니다' : '임베딩을 다시 시작했습니다');
      invalidate();
    },
    onError: (e) => setError(errorMessage(e)),
  });
  const remove = useMutation({
    mutationFn: () => api.deleteMaterial(id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['materials'] });
      navigate('/materials');
    },
    onError: (e) => setError(errorMessage(e)),
  });
  const saveChunk = useMutation({
    mutationFn: ({ chunkId, input }: { chunkId: string | 'new'; input: ChunkInput }) =>
      chunkId === 'new' ? api.addChunk(id, input) : api.updateChunk(chunkId, input),
    onSuccess: () => {
      setEditing(null);
      toast('청크를 저장했습니다');
      invalidate();
    },
    onError: (e) => setError(errorMessage(e)),
  });
  const deleteChunk = useMutation({
    mutationFn: (chunkId: string) => api.deleteChunk(chunkId),
    onSuccess: invalidate,
    onError: (e) => setError(errorMessage(e)),
  });

  const chunks = useMemo(() => (m?.chunks ?? []).filter((c) => !typeFilter || c.chunkType === typeFilter), [m, typeFilter]);
  const typeCounts = useMemo(() => {
    const map = new Map<ChunkType, number>();
    for (const c of m?.chunks ?? []) map.set(c.chunkType, (map.get(c.chunkType) ?? 0) + 1);
    return map;
  }, [m]);

  if (query.isLoading) return <Loading />;
  if (!m)
    return (
      <Page>
        <ErrorBanner message={query.error ? errorMessage(query.error) : '자료를 찾을 수 없습니다'} onRetry={() => query.refetch()} />
      </Page>
    );

  return (
    <Page wide>
      <div className="flex items-start gap-4 flex-wrap">
        <div className="flex-1 min-w-0">
          <Link to="/materials" className="text-xs text-slate-400 hover:text-slate-600">
            ← 자료 목록
          </Link>
          <h1 className="text-2xl font-bold text-slate-800 mt-0.5">{m.title}</h1>
          <div className="flex items-center gap-2 flex-wrap mt-1 text-xs text-slate-500">
            <StatusBadge status={m.status} message={m.error ?? m.statusMessage} />
            <span>{MATERIAL_KIND_LABEL[m.kind]}</span>
            {m.isExam && <Chip className="bg-indigo-50 text-indigo-700">시험</Chip>}
            <span>
              {m.sourceType}
              {m.fileName ? ` · ${m.fileName} (${bytes(m.fileSize)})` : ''}
            </span>
            <span>
              청크 {m.chunkCount} · 임베딩 {m.embeddedCount}
            </span>
            {m.structuredBy && <span>{m.structuredBy === 'AI' ? 'AI 구조화' : '규칙 분할'}</span>}
            <span>수정 {dateTime(m.updatedAt)}</span>
            <LabelList labels={m.labels} />
          </div>
          {m.statusMessage && processing && <div className="text-xs text-indigo-600 mt-1">{m.statusMessage}</div>}
          {m.status === 'FAILED' && m.error && (
            <div className="mt-2 text-sm text-red-700 bg-red-50 border border-red-100 rounded-lg px-3 py-2">{m.error}</div>
          )}
        </div>
        <div className="flex gap-2 flex-wrap">
          {m.isExam && (
            <Link to={`/exams/${m.id}`}>
              <Button>시험·성적</Button>
            </Link>
          )}
          {m.fileName && <Button onClick={() => api.downloadMaterialFile(m.id, m.fileName ?? 'file').catch((e) => setError(errorMessage(e)))}>원본</Button>}
          <Button disabled={processing || process.isPending} onClick={() => process.mutate(false)}>
            임베딩 다시
          </Button>
          <Button
            disabled={processing || process.isPending || m.scoreSetCount > 0}
            title={m.scoreSetCount > 0 ? '성적 세트가 있어 다시 구조화할 수 없습니다' : '추출·구조화부터 다시 합니다 (청크 편집 내용이 사라집니다)'}
            onClick={() => process.mutate(true)}
          >
            구조화 다시
          </Button>
          <ConfirmButton label="자료 삭제" variant="secondary" size="sm" confirmLabel="청크·성적까지 삭제" onConfirm={() => remove.mutate()} />
        </div>
      </div>

      <ErrorBanner message={error} />
      <MetaEditor m={m} onSaved={invalidate} />

      <div className="flex items-center gap-2 flex-wrap">
        <div className="font-semibold text-slate-700">청크</div>
        <select
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value as ChunkType | '')}
          aria-label="청크 종류 필터"
          className={`${inputClass} text-xs py-1`}
        >
          <option value="">전체 ({m.chunks.length})</option>
          {CHUNK_TYPES.filter((t) => typeCounts.has(t)).map((t) => (
            <option key={t} value={t}>
              {CHUNK_TYPE_LABEL[t]} ({typeCounts.get(t)})
            </option>
          ))}
        </select>
        <div className="ml-auto">
          <Button variant="primary" disabled={processing || editing === 'new'} onClick={() => setEditing('new')}>
            + 청크 추가
          </Button>
        </div>
      </div>

      {processing && m.chunks.length === 0 && <Loading label="자료를 처리하고 있습니다…" />}
      {!processing && m.chunks.length === 0 && editing !== 'new' && <Empty>청크가 없습니다.</Empty>}

      <div className="space-y-2">
        {chunks.map((c) =>
          editing === c.id ? (
            <ChunkEditor
              key={c.id}
              initial={chunkToInput(c)}
              aiLabelIds={c.labels.filter((l) => l.source === 'AI').map((l) => l.id)}
              saving={saveChunk.isPending}
              onCancel={() => setEditing(null)}
              onSave={(input) => saveChunk.mutate({ chunkId: c.id, input })}
            />
          ) : (
            <ChunkCard
              key={c.id}
              chunk={c}
              onEdit={() => setEditing(c.id)}
              onDelete={() => deleteChunk.mutate(c.id)}
              deleting={deleteChunk.isPending}
            />
          ),
        )}
        {editing === 'new' && (
          <ChunkEditor
            initial={emptyChunkInput(m.isExam)}
            saving={saveChunk.isPending}
            onCancel={() => setEditing(null)}
            onSave={(input) => saveChunk.mutate({ chunkId: 'new', input })}
          />
        )}
      </div>
    </Page>
  );
}
