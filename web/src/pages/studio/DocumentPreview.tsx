import clsx from 'clsx';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { DocumentDetail } from '../../api/types';
import { Chip, Segmented, Spinner } from '../../components/ui';
import { DOC_MODE_LABEL, EvidenceList, ScreenDocument, type DocMode } from '../../documents/DocumentView';
import { dateTime } from '../../lib/format';
import { RichText } from '../../lib/math';

type Tab = DocMode | 'evidence';

/** 작업실 오른쪽: 이 대화의 산출물 미리보기 */
export function DocumentPreview({
  doc,
  selectedVersion,
  onSelectVersion,
  writing,
  similarityWarn,
}: {
  doc: DocumentDetail | null;
  selectedVersion: number | null;
  onSelectVersion: (v: number) => void;
  writing: boolean;
  similarityWarn: number;
}) {
  const [tab, setTab] = useState<Tab>('student');
  const [collapsed, setCollapsed] = useState(false);
  const version = doc?.versions.find((v) => v.version === selectedVersion) ?? doc?.versions[doc.versions.length - 1] ?? null;

  // 산출물이 아직 없으면 빈 패널로 대화 폭을 뺏지 않는다
  if (collapsed || (!doc && !writing)) {
    return (
      <div className="w-10 shrink-0 border-l border-slate-200 bg-white flex flex-col items-center py-3">
        <button
          type="button"
          onClick={() => setCollapsed(false)}
          disabled={!doc && !writing}
          className="text-xs text-slate-500 hover:text-indigo-600 disabled:hover:text-slate-400 disabled:text-slate-400 [writing-mode:vertical-rl]"
        >
          {!doc && !writing ? '산출물 없음' : '산출물 미리보기 열기'}
        </button>
      </div>
    );
  }

  return (
    <div className="w-[clamp(340px,36vw,640px)] shrink-0 border-l border-slate-200 bg-slate-50 flex flex-col min-h-0">
      <div className="px-4 py-2.5 border-b border-slate-200 bg-white flex items-center gap-2 min-h-[52px]">
        <div className="font-semibold text-slate-800 text-sm">산출물</div>
        {doc && doc.versions.length > 0 && (
          <select
            value={version?.version ?? ''}
            onChange={(e) => onSelectVersion(Number(e.target.value))}
            aria-label="버전"
            className="text-xs border border-slate-300 rounded-md px-1.5 py-0.5 bg-white"
          >
            {[...doc.versions].reverse().map((v) => (
              <option key={v.version} value={v.version}>
                v{v.version} · {dateTime(v.createdAt)}
              </option>
            ))}
          </select>
        )}
        {writing && (
          <span className="inline-flex items-center gap-1 text-xs text-indigo-600">
            <Spinner className="text-indigo-500" />
            작성 중…
          </span>
        )}
        <div className="ml-auto flex items-center gap-2">
          {doc && (
            <>
              <Link to={`/documents/${doc.id}`} className="text-xs text-indigo-600 hover:underline">
                편집
              </Link>
              {version && (
                <a
                  href={`/print/documents/${doc.id}?version=${version.version}&doc=all`}
                  target="_blank"
                  rel="noreferrer"
                  className="text-xs text-indigo-600 hover:underline"
                >
                  인쇄
                </a>
              )}
            </>
          )}
          <button type="button" onClick={() => setCollapsed(true)} className="text-xs text-slate-400 hover:text-slate-600" aria-label="미리보기 접기">
            접기
          </button>
        </div>
      </div>
      {!version ? (
        <div className="flex-1 flex items-center justify-center text-sm text-slate-400 px-8 text-center">
          {writing ? '산출물을 작성하고 있습니다…' : '요청하면 만들어진 문제·자료가 여기에 나타납니다.'}
        </div>
      ) : (
        <>
          <div className="px-4 pt-3 flex items-center gap-2">
            <Segmented<Tab>
              label="보기"
              value={tab}
              onChange={setTab}
              options={[
                { value: 'student', label: DOC_MODE_LABEL.student },
                { value: 'answer', label: DOC_MODE_LABEL.answer },
                { value: 'evidence', label: '근거' },
              ]}
            />
            {version.payload.changeNote && (
              <span className="text-[11px] text-slate-500 truncate" title={version.payload.changeNote}>
                변경: {version.payload.changeNote}
              </span>
            )}
          </div>
          {version.warnings.length > 0 && (
            <ul className="mx-4 mt-2 space-y-1">
              {version.warnings.map((w, i) => (
                <li key={i} className="text-xs text-amber-800 bg-amber-50 border border-amber-100 rounded-md px-2 py-1">
                  {w}
                </li>
              ))}
            </ul>
          )}
          <div className={clsx('flex-1 overflow-y-auto px-4 py-3')}>
            {tab === 'evidence' ? (
              <div className="space-y-4">
                {version.payload.summary && (
                  <div>
                    <div className="text-xs font-semibold text-slate-500 mb-1">개요·의도</div>
                    <RichText text={version.payload.summary} className="text-sm" />
                  </div>
                )}
                <div>
                  <div className="text-xs font-semibold text-slate-500 mb-1">근거</div>
                  <EvidenceList payload={version.payload} />
                </div>
                {version.similar.length > 0 && (
                  <div>
                    <div className="text-xs font-semibold text-slate-500 mb-1">비슷한 기존 자료</div>
                    <ul className="space-y-1">
                      {[...version.similar]
                        .sort((a, b) => b.similarity - a.similarity)
                        .map((s, i) => (
                          <li key={i} className="text-xs flex gap-2">
                            <Chip className="bg-slate-100 text-slate-600">{s.itemNumber}번</Chip>
                            <Link to={`/materials/${s.materialId}`} className="flex-1 text-indigo-600 hover:underline truncate">
                              {s.materialTitle} {s.position ?? ''}
                            </Link>
                            <span className="tabular-nums text-slate-500">{s.similarity.toFixed(2)}</span>
                          </li>
                        ))}
                    </ul>
                  </div>
                )}
              </div>
            ) : (
              <ScreenDocument version={version} mode={tab} similarityWarn={similarityWarn} />
            )}
          </div>
        </>
      )}
    </div>
  );
}
