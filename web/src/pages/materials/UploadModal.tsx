import { useState } from 'react';
import { api, errorMessage } from '../../api/client';
import type { MaterialKind, MaterialSummary } from '../../api/types';
import { LabelPicker } from '../../components/pickers';
import { Button, ErrorBanner, Field, Modal, Segmented, Toggle, inputClass, textareaClass } from '../../components/ui';
import { MATERIAL_KINDS, MATERIAL_KIND_LABEL } from '../../lib/labels';

const ACCEPT = '.pdf,.docx,.txt,.md,.markdown,application/pdf,text/plain,text/markdown';
const MAX_BYTES = 50 * 1024 * 1024;

export function UploadModal({
  open,
  onClose,
  onUploaded,
}: {
  open: boolean;
  onClose: () => void;
  onUploaded: (m: MaterialSummary) => void;
}) {
  const [source, setSource] = useState<'file' | 'text'>('file');
  const [file, setFile] = useState<File | null>(null);
  const [text, setText] = useState('');
  const [title, setTitle] = useState('');
  const [kind, setKind] = useState<MaterialKind>('WORKSHEET');
  const [description, setDescription] = useState('');
  const [labelIds, setLabelIds] = useState<string[]>([]);
  const [isExam, setIsExam] = useState(false);
  const [autoLabel, setAutoLabel] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setFile(null);
    setText('');
    setTitle('');
    setDescription('');
    setLabelIds([]);
    setIsExam(false);
    setError(null);
  };

  const pickFile = (f: File | null) => {
    setFile(f);
    if (f && !title) setTitle(f.name.replace(/\.[^.]+$/, ''));
  };

  const valid = title.trim() && (source === 'file' ? !!file : text.trim().length > 0);

  const submit = async () => {
    if (!valid) return;
    if (source === 'file' && file && file.size > MAX_BYTES) {
      setError('파일은 50MB 까지 올릴 수 있습니다.');
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const m = await api.uploadMaterial({
        file: source === 'file' ? file : null,
        text: source === 'text' ? text : null,
        title: title.trim(),
        kind,
        description: description.trim() || null,
        labelIds,
        isExam,
        autoLabel,
      });
      reset();
      onUploaded(m);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title="자료 올리기">
      <div className="space-y-4">
        <Segmented
          label="입력 방식"
          value={source}
          onChange={setSource}
          options={[
            { value: 'file', label: '파일 (PDF·DOCX·TXT·MD)' },
            { value: 'text', label: '텍스트 붙여넣기' },
          ]}
        />
        {source === 'file' ? (
          <label
            className="block border-2 border-dashed border-slate-300 rounded-lg p-6 text-center cursor-pointer hover:border-indigo-400"
            onDragOver={(e) => e.preventDefault()}
            onDrop={(e) => {
              e.preventDefault();
              pickFile(e.dataTransfer.files?.[0] ?? null);
            }}
          >
            <input type="file" accept={ACCEPT} className="hidden" onChange={(e) => pickFile(e.target.files?.[0] ?? null)} />
            {file ? (
              <span className="text-sm text-slate-700">
                {file.name} <span className="text-slate-400">({(file.size / 1024 / 1024).toFixed(1)} MB)</span>
              </span>
            ) : (
              <span className="text-sm text-slate-400">파일을 끌어다 놓거나 눌러서 고르세요 (최대 50MB)</span>
            )}
          </label>
        ) : (
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            rows={8}
            placeholder="문제·지문·개념 설명 등을 붙여넣으세요. 수식은 $…$ LaTeX 로 쓸 수 있습니다."
            className={textareaClass}
          />
        )}
        <div className="grid grid-cols-2 gap-3">
          <Field label="제목">
            <input value={title} onChange={(e) => setTitle(e.target.value)} className={`${inputClass} w-full`} />
          </Field>
          <Field label="종류">
            <select
              value={kind}
              onChange={(e) => {
                const k = e.target.value as MaterialKind;
                setKind(k);
                if (k === 'EXAM') setIsExam(true);
              }}
              className={`${inputClass} w-full`}
            >
              {MATERIAL_KINDS.map((k) => (
                <option key={k} value={k}>
                  {MATERIAL_KIND_LABEL[k]}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <Field label="설명 (선택)">
          <input value={description} onChange={(e) => setDescription(e.target.value)} className={`${inputClass} w-full`} />
        </Field>
        <div className="space-y-1">
          <span className="text-xs font-medium text-slate-500">라벨 (자료 전체에 붙음)</span>
          <LabelPicker value={labelIds} onChange={setLabelIds} />
        </div>
        <div className="space-y-2.5 bg-slate-50 rounded-lg p-3">
          <div className="flex items-center gap-3">
            <Toggle checked={isExam} onChange={setIsExam} label="시험 자료" />
            <div className="text-sm">
              <div className="font-medium text-slate-700">시험 자료</div>
              <div className="text-xs text-slate-500">문항마다 배점·정답을 두고 성적을 입력해 정답률·변별도를 계산합니다.</div>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <Toggle checked={autoLabel} onChange={setAutoLabel} label="AI 라벨 자동 부여" />
            <div className="text-sm">
              <div className="font-medium text-slate-700">AI 라벨 자동 부여</div>
              <div className="text-xs text-slate-500">기존 라벨 중 맞는 것을 청크마다 붙입니다 (나중에 고칠 수 있음).</div>
            </div>
          </div>
        </div>
        <ErrorBanner message={error} />
        <div className="flex justify-end gap-2">
          <Button onClick={onClose}>취소</Button>
          <Button variant="primary" onClick={submit} disabled={!valid || busy}>
            {busy ? '올리는 중…' : '올리고 구조화 시작'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}
