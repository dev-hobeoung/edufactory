import { useState } from 'react';
import { api, errorMessage } from '../../api/client';
import type { ExamItem, ImportResult, ScoreMode, ScoreSet } from '../../api/types';
import { Button, ErrorBanner, Modal, Segmented, Toggle, textareaClass } from '../../components/ui';

/** CSV·TSV(엑셀 붙여넣기) 성적 가져오기 — 첫 행 헤더: 학생 키, 문항 번호… */
export function ImportModal({
  open,
  set,
  items,
  onClose,
  onImported,
}: {
  open: boolean;
  set: ScoreSet;
  items: ExamItem[];
  onClose: () => void;
  onImported: () => void;
}) {
  const [mode, setMode] = useState<ScoreMode>('SCORE');
  const [text, setText] = useState('');
  const [replace, setReplace] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);

  const header = ['학생', ...items.map((i) => i.itemNo ?? '?')].join('\t');

  const readFile = async (f: File | null) => {
    if (!f) return;
    setText(await f.text());
  };

  const submit = async () => {
    setBusy(true);
    setError(null);
    try {
      const r = await api.importScores(set.id, { mode, text, replace });
      setResult(r);
      if (r.imported > 0 && r.unknownColumns.length === 0 && r.warnings.length === 0) {
        setText('');
        setResult(null);
        onImported();
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} title={`성적 가져오기 · ${set.name}`} width="max-w-3xl">
      <div className="space-y-3">
        <p className="text-xs text-slate-500 leading-relaxed">
          엑셀에서 범위를 복사해 붙여넣거나 CSV 파일을 고르세요. 첫 행은 헤더입니다 — 첫 열은 학생 키(학번·번호), 나머지 열은
          문항 번호입니다. 빈 칸은 미응답으로 처리합니다.
        </p>
        <div className="text-[11px] bg-slate-50 border border-slate-200 rounded px-2 py-1 font-mono overflow-x-auto whitespace-pre">
          {header}
          {'\n'}S001{'\t'}…
        </div>
        <div className="flex items-center gap-3 flex-wrap">
          <Segmented
            label="값의 의미"
            value={mode}
            onChange={setMode}
            options={[
              { value: 'SCORE', label: '득점' },
              { value: 'RESPONSE', label: '학생 응답 (자동 채점)' },
            ]}
          />
          <label className="flex items-center gap-2 text-sm text-slate-600">
            <Toggle checked={replace} onChange={setReplace} label="기존 성적 교체" />
            {replace ? '이 세트의 기존 성적을 모두 교체' : '기존 성적에 추가·덮어쓰기'}
          </label>
          <label className="ml-auto text-xs text-indigo-600 cursor-pointer hover:underline">
            CSV 파일 고르기
            <input type="file" accept=".csv,.tsv,.txt,text/csv" className="hidden" onChange={(e) => readFile(e.target.files?.[0] ?? null)} />
          </label>
        </div>
        <textarea
          value={text}
          onChange={(e) => setText(e.target.value)}
          rows={12}
          placeholder={header}
          className={`${textareaClass} font-mono text-xs`}
        />
        <ErrorBanner message={error} />
        {result && (
          <div className="text-sm space-y-1 bg-slate-50 rounded-lg px-3 py-2">
            <div>{result.imported}명을 가져왔습니다.</div>
            {result.unknownColumns.length > 0 && (
              <div className="text-amber-700">문항 번호와 맞지 않아 건너뛴 열: {result.unknownColumns.join(', ')}</div>
            )}
            {result.warnings.map((w, i) => (
              <div key={i} className="text-amber-700 text-xs">
                {w}
              </div>
            ))}
          </div>
        )}
        <div className="flex justify-end gap-2">
          {result ? (
            <Button
              variant="primary"
              onClick={() => {
                setResult(null);
                setText('');
                onImported();
              }}
            >
              확인
            </Button>
          ) : (
            <>
              <Button onClick={onClose}>취소</Button>
              <Button variant="primary" disabled={!text.trim() || busy} onClick={submit}>
                {busy ? '가져오는 중…' : '가져오기'}
              </Button>
            </>
          )}
        </div>
      </div>
    </Modal>
  );
}
