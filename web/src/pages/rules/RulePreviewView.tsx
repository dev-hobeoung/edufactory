import type { RulePreview } from '../../api/types';
import { Chip, Loading } from '../../components/ui';

/** 규칙 미리보기 결과: 적용된 규칙 목록 + 실제로 붙을 블록 */
export function RulePreviewView({ preview, loading }: { preview: RulePreview | null; loading: boolean }) {
  if (loading) return <Loading label="규칙을 평가하는 중…" />;
  if (!preview) return null;
  return (
    <div className="space-y-3">
      {preview.applied.length === 0 ? (
        <div className="text-sm text-slate-500 bg-slate-50 rounded-lg px-3 py-2">조건에 맞는 규칙이 없습니다. 주입되는 컨텍스트가 없습니다.</div>
      ) : (
        <ul className="space-y-1">
          {preview.applied.map((r) => (
            <li key={r.id} className="flex items-center gap-2 text-sm">
              <Chip className="bg-violet-50 text-violet-700">적용</Chip>
              <span className="font-medium flex-1">{r.name}</span>
              <span className="text-xs text-slate-400 tabular-nums">
                자료 {r.materialCount} · 검색 {r.chunkCount} · {r.chars.toLocaleString()}자
              </span>
            </li>
          ))}
        </ul>
      )}
      {preview.block && (
        <pre className="text-xs bg-slate-900 text-slate-100 rounded-lg p-3 overflow-auto max-h-[50vh] whitespace-pre-wrap break-words">
          {preview.block}
        </pre>
      )}
    </div>
  );
}
