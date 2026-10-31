import clsx from 'clsx';
import type { DocPayload, DocumentVersion, Item, ItemPrediction, SimilarHit } from '../api/types';
import { Chip } from '../components/ui';
import { CHOICE_TYPES, CONFIDENCE_LABEL, OUTPUT_KIND_LABEL, QUESTION_TYPE_LABEL } from '../lib/labels';
import { pct, rateTone, SIM_TONE_CLASS, similarityTone } from '../lib/format';
import { RichText } from '../lib/math';

export type DocMode = 'student' | 'answer';
export const DOC_MODE_LABEL: Record<DocMode, string> = { student: '문제지', answer: '정답·해설' };

export function itemCount(p: DocPayload): number {
  return p.sections.reduce((n, s) => n + s.items.length, 0);
}

export function totalScore(p: DocPayload): number | null {
  let total = 0;
  let any = false;
  for (const s of p.sections)
    for (const it of s.items)
      if (it.score !== null && it.score !== undefined) {
        total += it.score;
        any = true;
      }
  return any ? total : null;
}

/** 서버가 일부 필드를 빼고 보내도 그릴 수 있게 채운다 */
export function normalizePayload(p: DocPayload): DocPayload {
  return {
    ...p,
    audience: p.audience ?? null,
    summary: p.summary ?? null,
    instructions: p.instructions ?? null,
    changeNote: p.changeNote ?? null,
    evidence: p.evidence ?? [],
    sections: (p.sections ?? []).map((s) => ({
      title: s.title ?? null,
      body: s.body ?? null,
      items: (s.items ?? []).map((it) => ({
        ...it,
        choices: it.choices ?? [],
        criteria: it.criteria ?? [],
        tags: it.tags ?? [],
        sourceRefs: it.sourceRefs ?? [],
        explanation: it.explanation ?? null,
        score: it.score ?? null,
        difficulty: it.difficulty ?? null,
      })),
    })),
  };
}

function Choices({ item, showAnswer }: { item: Item; showAnswer: boolean }) {
  if (!CHOICE_TYPES.includes(item.type) || item.choices.length === 0) return null;
  const correct = new Set(
    item.answer
      .split(/[,\s]+/)
      .map((s) => s.trim())
      .filter(Boolean),
  );
  const inline = item.choices.every((c) => c.text.length <= 14) && item.choices.length <= 5;
  return (
    <ol className={clsx('mt-1.5', inline ? 'flex flex-wrap gap-x-6 gap-y-1' : 'space-y-1')}>
      {item.choices.map((c) => {
        const ok = showAnswer && correct.has(c.label);
        return (
          <li key={c.label} className={clsx('flex gap-1.5', ok && 'font-semibold text-indigo-700')}>
            <span className="shrink-0">{c.label}</span>
            <RichText text={c.text} className="flex-1" />
          </li>
        );
      })}
    </ol>
  );
}

/** 학생이 답을 쓸 칸 (서술·단답) */
function AnswerSpace({ item }: { item: Item }) {
  if (item.type === 'ESSAY') return <div className="mt-2 h-32 border border-slate-300 rounded" />;
  if (item.type === 'SHORT_ANSWER' || item.type === 'FILL_BLANK')
    return <div className="mt-2 h-8 w-1/2 border-b border-slate-400" />;
  return null;
}

/** A4 문제지 (학생용) — 인쇄와 미리보기가 같이 쓴다 */
export function StudentPaper({ payload: p, version }: { payload: DocPayload; version?: number }) {
  const total = totalScore(p);
  return (
    <section className="doc-page print-page bg-white shadow mx-auto w-[210mm] min-h-[297mm] p-[16mm] text-[10.5pt] leading-relaxed text-slate-900">
      <header className="border-b-2 border-slate-900 pb-2 mb-4 flex items-end justify-between gap-4">
        <div className="min-w-0">
          <div className="text-[9pt] tracking-widest text-slate-500">{OUTPUT_KIND_LABEL[p.kind] ?? '자료'}</div>
          <h1 className="text-[16pt] font-bold leading-tight">{p.title}</h1>
        </div>
        <div className="text-right text-[9pt] text-slate-500 shrink-0">
          {p.audience && <div>{p.audience}</div>}
          {total !== null && <div>총점 {total}점</div>}
          {version !== undefined && <div>v{version}</div>}
        </div>
      </header>
      {itemCount(p) > 0 && (
        <table className="w-full border-collapse text-[9.5pt] mb-4">
          <tbody>
            <tr>
              <th className="border border-slate-400 bg-slate-50 px-2 py-1.5 w-16 font-semibold">반·번호</th>
              <td className="border border-slate-400 px-2 py-1.5 w-[40%]" />
              <th className="border border-slate-400 bg-slate-50 px-2 py-1.5 w-16 font-semibold">이름</th>
              <td className="border border-slate-400 px-2 py-1.5" />
            </tr>
          </tbody>
        </table>
      )}
      {p.instructions && (
        <div className="avoid-break mb-4 bg-slate-50 border border-slate-200 rounded px-3 py-2 text-[9.5pt]">
          <RichText text={p.instructions} />
        </div>
      )}
      {p.sections.map((s, si) => (
        <div key={si} className="mb-6">
          {s.title && <div className="font-bold text-[11.5pt] border-b border-slate-300 pb-1 mb-2">{s.title}</div>}
          {s.body && (
            <div className={clsx('mb-3', s.items.length > 0 && 'avoid-break border border-slate-400 rounded p-3')}>
              <RichText text={s.body} />
            </div>
          )}
          <ol className="space-y-4">
            {s.items.map((it) => (
              <li key={it.number} className="avoid-break">
                <div className="flex gap-2">
                  <span className="font-bold shrink-0">{it.number}.</span>
                  <div className="flex-1 min-w-0">
                    {it.type === 'FLASHCARD' ? (
                      <div className="border border-slate-300 rounded p-2">
                        <RichText text={it.stem} />
                      </div>
                    ) : (
                      <RichText text={it.stem} />
                    )}
                    <Choices item={it} showAnswer={false} />
                    <AnswerSpace item={it} />
                  </div>
                  {it.score !== null && <span className="shrink-0 text-[9pt] text-slate-500">[{it.score}점]</span>}
                </div>
              </li>
            ))}
          </ol>
        </div>
      ))}
      <footer className="mt-8 pt-2 border-t border-slate-300 text-[8pt] text-slate-400 flex justify-between">
        <span>EDU FACTORY</span>
        <span>{p.title}</span>
      </footer>
    </section>
  );
}

/** A4 정답·해설지 (교사용) */
export function AnswerSheet({ payload: p, version }: { payload: DocPayload; version?: number }) {
  const total = totalScore(p);
  return (
    <section className="doc-page print-page bg-white shadow mx-auto w-[210mm] min-h-[297mm] p-[16mm] text-[10pt] leading-relaxed text-slate-900">
      <header className="border-b-2 border-slate-900 pb-2 mb-4 flex items-end justify-between gap-4">
        <div className="min-w-0">
          <div className="text-[9pt] tracking-widest text-slate-500">{OUTPUT_KIND_LABEL[p.kind] ?? '자료'} · 정답·해설</div>
          <h1 className="text-[15pt] font-bold leading-tight">{p.title}</h1>
        </div>
        <div className="text-right text-[9pt] text-slate-500 shrink-0">
          {total !== null && <div>총점 {total}점</div>}
          {version !== undefined && <div>v{version}</div>}
        </div>
      </header>
      {p.summary && (
        <div className="avoid-break mb-4 bg-slate-50 border border-slate-200 rounded px-3 py-2">
          <div className="text-[9pt] font-semibold text-slate-500">개요·의도</div>
          <RichText text={p.summary} />
        </div>
      )}
      {itemCount(p) === 0 && (
        <div className="text-slate-500 text-[9.5pt]">이 자료에는 문항이 없습니다. 본문은 문제지(자료) 화면에서 확인하세요.</div>
      )}
      {p.sections.map((s, si) =>
        s.items.length === 0 ? null : (
          <div key={si} className="mb-5">
            {s.title && <div className="font-bold text-[11pt] border-b border-slate-300 pb-1 mb-2">{s.title}</div>}
            {s.items.map((it) => (
              <div key={it.number} className="avoid-break mb-4">
                <div className="flex gap-2 items-baseline">
                  <span className="font-semibold shrink-0">{it.number}.</span>
                  <RichText text={it.stem} className="flex-1 text-slate-600 text-[9.5pt]" />
                  <span className="shrink-0 text-[9pt] text-slate-500">
                    {QUESTION_TYPE_LABEL[it.type]}
                    {it.score !== null ? ` · ${it.score}점` : ''}
                    {it.difficulty !== null ? ` · 예상 ${pct(it.difficulty)}` : ''}
                  </span>
                </div>
                <Choices item={it} showAnswer />
                <div className="mt-1.5 pl-3 border-l-2 border-indigo-300 space-y-1">
                  <div>
                    <span className="text-[9pt] font-semibold text-indigo-700 mr-1.5">정답</span>
                    <RichText text={it.answer || '—'} className="inline-block align-top" />
                  </div>
                  {it.explanation && (
                    <div>
                      <div className="text-[9pt] font-semibold text-indigo-700">해설</div>
                      <RichText text={it.explanation} />
                    </div>
                  )}
                </div>
                {it.criteria.length > 0 && (
                  <table className="w-full border-collapse text-[9pt] mt-2">
                    <thead>
                      <tr className="bg-slate-100">
                        <th className="border border-slate-300 px-2 py-0.5 text-left">채점 기준</th>
                        <th className="border border-slate-300 px-2 py-0.5 w-12">배점</th>
                      </tr>
                    </thead>
                    <tbody>
                      {it.criteria.map((c, i) => (
                        <tr key={i}>
                          <td className="border border-slate-300 px-2 py-0.5">
                            <RichText text={c.text} />
                          </td>
                          <td className="border border-slate-300 px-2 py-0.5 text-center tabular-nums">{c.score}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            ))}
          </div>
        ),
      )}
      <footer className="mt-8 pt-2 border-t border-slate-300 text-[8pt] text-slate-400 flex justify-between">
        <span>EDU FACTORY · 정답·해설 (교사용)</span>
        <span>{p.title}</span>
      </footer>
    </section>
  );
}

export function A4Document({ mode, payload, version }: { mode: DocMode; payload: DocPayload; version?: number }) {
  return mode === 'student' ? <StudentPaper payload={payload} version={version} /> : <AnswerSheet payload={payload} version={version} />;
}

// ── 화면용 미리보기 (작업실 오른쪽·산출물 화면) — 문항마다 예상 정답률·유사도 표시 ──

function ItemAnnotations({
  prediction,
  similar,
  similarityWarn,
}: {
  prediction: ItemPrediction | undefined;
  similar: SimilarHit | undefined;
  similarityWarn: number;
}) {
  if (!prediction && !similar) return null;
  const tone = similarityTone(similar?.similarity, similarityWarn);
  return (
    <div className="flex flex-wrap gap-1 mt-1.5">
      {prediction && (
        <Chip
          className={rateTone(prediction.expectedRate)}
          title={
            prediction.neighbors.length
              ? `근거: ${prediction.neighbors
                  .slice(0, 3)
                  .map((n) => `${n.materialTitle} ${n.position ?? ''} (유사도 ${n.similarity.toFixed(2)}, 정답률 ${pct(n.avgRate)}, n=${n.n})`)
                  .join(' / ')}`
              : '비슷한 문항의 성적 데이터가 없습니다'
          }
        >
          예측 정답률 {pct(prediction.expectedRate)} · 신뢰도 {CONFIDENCE_LABEL[prediction.confidence]}
        </Chip>
      )}
      {similar && (
        <Chip
          className={SIM_TONE_CLASS[tone]}
          title={`${similar.materialTitle}${similar.position ? ` ${similar.position}` : ''}`}
        >
          자료 유사도 {similar.similarity.toFixed(2)}
          {tone === 'red' ? ' · 기존 자료와 거의 같음' : ''}
        </Chip>
      )}
    </div>
  );
}

export function ScreenDocument({
  version,
  mode,
  similarityWarn,
}: {
  version: DocumentVersion;
  mode: DocMode;
  similarityWarn: number;
}) {
  const p = normalizePayload(version.payload);
  const total = totalScore(p);
  const predictionOf = (n: string) => version.prediction?.find((x) => x.itemNumber === n);
  const similarOf = (n: string) =>
    [...(version.similar ?? [])].filter((x) => x.itemNumber === n).sort((a, b) => b.similarity - a.similarity)[0];
  const showAnswer = mode === 'answer';
  return (
    <div className="space-y-4 text-sm text-slate-800">
      <div>
        <div className="text-[11px] text-slate-400">
          {OUTPUT_KIND_LABEL[p.kind] ?? p.kind}
          {p.audience ? ` · ${p.audience}` : ''}
          {total !== null ? ` · 총점 ${total}점` : ''} · 문항 {itemCount(p)}개
        </div>
        <div className="text-lg font-bold text-slate-900 leading-snug">{p.title}</div>
      </div>
      {showAnswer && p.summary && (
        <div className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-2">
          <div className="text-[11px] font-semibold text-slate-500">개요·의도</div>
          <RichText text={p.summary} />
        </div>
      )}
      {p.instructions && (
        <div className="bg-slate-50 border border-slate-200 rounded-lg px-3 py-2 text-[13px]">
          <RichText text={p.instructions} />
        </div>
      )}
      {p.sections.map((s, si) => (
        <div key={si} className="space-y-3">
          {s.title && <div className="font-semibold text-slate-900 border-b border-slate-200 pb-1">{s.title}</div>}
          {s.body && (
            <div className={clsx(s.items.length > 0 && 'border border-slate-200 rounded-lg p-3 bg-white')}>
              <RichText text={s.body} />
            </div>
          )}
          {s.items.map((it) => (
            <div key={it.number} className="bg-white border border-slate-200 rounded-lg p-3">
              <div className="flex gap-2">
                <span className="font-bold shrink-0">{it.number}.</span>
                <div className="flex-1 min-w-0">
                  <RichText text={it.stem} />
                  <Choices item={it} showAnswer={showAnswer} />
                </div>
                <div className="shrink-0 text-right space-y-0.5">
                  <div className="text-[11px] text-slate-400">{QUESTION_TYPE_LABEL[it.type] ?? it.type}</div>
                  {it.score !== null && <div className="text-xs text-slate-500">{it.score}점</div>}
                </div>
              </div>
              {showAnswer && (
                <div className="mt-2 pl-3 border-l-2 border-indigo-300 space-y-1 text-[13px]">
                  <div>
                    <span className="text-xs font-semibold text-indigo-700 mr-1.5">정답</span>
                    <RichText text={it.answer || '—'} className="inline-block align-top" />
                  </div>
                  {it.explanation && <RichText text={it.explanation} className="text-slate-600" />}
                  {it.criteria.length > 0 && (
                    <ul className="text-xs text-slate-600 space-y-0.5">
                      {it.criteria.map((c, i) => (
                        <li key={i} className="flex gap-2">
                          <span className="flex-1">
                            <RichText text={c.text} />
                          </span>
                          <span className="tabular-nums shrink-0">{c.score}점</span>
                        </li>
                      ))}
                    </ul>
                  )}
                  {it.difficulty !== null && (
                    <div className="text-[11px] text-slate-400">작성자 예상 정답률 {pct(it.difficulty)}</div>
                  )}
                </div>
              )}
              {(it.tags.length > 0 && showAnswer) && (
                <div className="flex flex-wrap gap-1 mt-1.5">
                  {it.tags.map((t) => (
                    <Chip key={t} className="bg-slate-100 text-slate-500">
                      #{t}
                    </Chip>
                  ))}
                </div>
              )}
              <ItemAnnotations prediction={predictionOf(it.number)} similar={similarOf(it.number)} similarityWarn={similarityWarn} />
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}

const EVIDENCE_LABEL: Record<string, string> = {
  chunk: '자료',
  item_stat: '문항 통계',
  exam_stat: '시험 통계',
  rule: '규칙',
  material: '자료',
  web: '웹',
};

export function EvidenceList({ payload }: { payload: DocPayload }) {
  const list = payload.evidence ?? [];
  if (list.length === 0) return <div className="text-xs text-slate-400">근거가 기록되지 않았습니다.</div>;
  return (
    <ul className="space-y-1.5">
      {list.map((e, i) => (
        <li key={i} className="text-xs flex gap-2 items-start">
          <Chip className="bg-slate-100 text-slate-600 shrink-0">{EVIDENCE_LABEL[e.kind] ?? e.kind}</Chip>
          <span className="flex-1 text-slate-600">
            {e.summary}
            {e.kind === 'web' ? (
              <a href={e.ref} target="_blank" rel="noreferrer" className="block text-indigo-600 truncate hover:underline">
                {e.ref}
              </a>
            ) : (
              <span className="block text-slate-400 font-mono text-[10px] truncate">{e.ref}</span>
            )}
          </span>
        </li>
      ))}
    </ul>
  );
}
