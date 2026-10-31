import clsx from 'clsx';
import { useEffect, useMemo, useState } from 'react';
import type { ConversationSummary } from '../../api/types';
import { Button, Segmented, Spinner, inputClass } from '../../components/ui';
import { dayGroup, relTime } from '../../lib/format';

type Tab = 'active' | 'archived';

export function ConversationList({
  items,
  archivedItems,
  activeId,
  runningIds,
  onSelect,
  onNew,
  onRename,
  onArchive,
  onShowArchived,
  onUnarchive,
  onDelete,
  loading,
}: {
  items: ConversationSummary[];
  /** 보관한 대화 — 보관됨 탭을 처음 열 때 읽는다 (null = 아직 안 읽음) */
  archivedItems: ConversationSummary[] | null;
  activeId: string | null;
  /** 답변을 만들고 있는 대화 */
  runningIds: string[];
  onSelect: (id: string) => void;
  onNew: () => void;
  onRename: (id: string, title: string) => void;
  onArchive: (id: string) => void;
  onShowArchived: () => void;
  onUnarchive: (id: string) => void;
  /** 완전 삭제 (산출물은 남는다) */
  onDelete: (id: string) => void;
  loading: boolean;
}) {
  const [tab, setTab] = useState<Tab>('active');
  const [q, setQ] = useState('');
  /** 삭제 확인 대기 중인 대화 — 3초 안에 한 번 더 눌러야 지운다 */
  const [armed, setArmed] = useState<string | null>(null);

  useEffect(() => {
    if (!armed) return;
    const t = setTimeout(() => setArmed(null), 3000);
    return () => clearTimeout(t);
  }, [armed]);

  const archivedTab = tab === 'archived';
  const shown = archivedTab ? (archivedItems ?? []) : items;
  const listLoading = archivedTab ? archivedItems === null : loading;
  const [editing, setEditing] = useState<string | null>(null);
  const [draftTitle, setDraftTitle] = useState('');

  const groups = useMemo(() => {
    const needle = q.trim().toLowerCase();
    const filtered = needle ? shown.filter((c) => c.title.toLowerCase().includes(needle)) : shown;
    const map = new Map<string, ConversationSummary[]>();
    for (const c of filtered) {
      const g = dayGroup(c.updatedAt);
      map.set(g, [...(map.get(g) ?? []), c]);
    }
    return [...map.entries()];
  }, [shown, q]);

  const commitRename = (id: string) => {
    const t = draftTitle.trim();
    if (t) onRename(id, t);
    setEditing(null);
  };

  return (
    <div className="w-52 xl:w-60 shrink-0 border-r border-slate-200 bg-white flex flex-col min-h-0">
      <div className="p-3">
        <Button variant="primary" className="w-full py-2" onClick={onNew}>
          + 새 대화
        </Button>
      </div>
      <div className="px-3 pb-2">
        <Segmented<Tab>
          label="대화 목록"
          value={tab}
          onChange={(t) => {
            setTab(t);
            setArmed(null);
            if (t === 'archived') onShowArchived();
          }}
          options={[
            { value: 'active', label: '대화' },
            { value: 'archived', label: archivedItems ? `보관됨 ${archivedItems.length}` : '보관됨' },
          ]}
        />
      </div>
      <div className="px-3 pb-2">
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="대화 검색"
          aria-label="대화 검색"
          className={`${inputClass} w-full text-xs py-1.5 px-2.5`}
        />
      </div>
      <div className="flex-1 overflow-y-auto px-2 pb-3 space-y-0.5 text-sm">
        {listLoading && shown.length === 0 && <div className="text-xs text-slate-400 px-2 py-4">불러오는 중…</div>}
        {!listLoading && groups.length === 0 && (
          <div className="text-xs text-slate-400 px-2 py-4">
            {q ? '검색 결과가 없습니다' : archivedTab ? '보관한 대화가 없습니다' : '아직 대화가 없습니다'}
          </div>
        )}
        {groups.map(([group, list]) => (
          <div key={group}>
            <div className="text-[11px] text-slate-400 px-2 pt-3 pb-1">{group}</div>
            {list.map((c) => {
              const active = c.id === activeId;
              return (
                <div
                  key={c.id}
                  className={clsx(
                    'group px-2.5 py-2 rounded-lg cursor-pointer',
                    active ? 'bg-indigo-50 text-indigo-900' : 'hover:bg-slate-50',
                  )}
                  onClick={() => editing !== c.id && onSelect(c.id)}
                >
                  {editing === c.id ? (
                    <input
                      autoFocus
                      value={draftTitle}
                      aria-label="대화 이름"
                      onChange={(e) => setDraftTitle(e.target.value)}
                      onBlur={() => commitRename(c.id)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') commitRename(c.id);
                        if (e.key === 'Escape') setEditing(null);
                      }}
                      className={`${inputClass} w-full text-xs py-1 px-2`}
                    />
                  ) : (
                    <div className="flex items-center gap-1.5 min-w-0">
                      {runningIds.includes(c.id) && (
                        <span title="답변 생성 중" className="shrink-0 inline-flex">
                          <Spinner className="text-indigo-500" />
                        </span>
                      )}
                      <div className={clsx('truncate', active && 'font-medium')}>{c.title}</div>
                    </div>
                  )}
                  <div className="flex items-center gap-1 mt-0.5">
                    <div className={clsx('text-[11px] flex-1 truncate', active ? 'text-indigo-500' : 'text-slate-400')}>
                      {c.currentVersion ? `산출물 v${c.currentVersion}` : '대화만'} · {relTime(c.updatedAt)}
                    </div>
                    {archivedTab ? (
                      <div
                        className={clsx(
                          'gap-1 text-[11px] text-slate-400',
                          armed === c.id ? 'flex' : 'hidden group-hover:flex',
                        )}
                      >
                        <button
                          type="button"
                          className="hover:text-slate-700"
                          onClick={(e) => {
                            e.stopPropagation();
                            onUnarchive(c.id);
                          }}
                        >
                          복원
                        </button>
                        <button
                          type="button"
                          title="대화 기록을 완전히 지웁니다 (산출물은 남습니다)"
                          className={armed === c.id ? 'text-red-600 font-medium' : 'hover:text-red-600'}
                          onClick={(e) => {
                            e.stopPropagation();
                            if (armed === c.id) {
                              setArmed(null);
                              onDelete(c.id);
                            } else {
                              setArmed(c.id);
                            }
                          }}
                        >
                          {armed === c.id ? '정말 삭제' : '삭제'}
                        </button>
                      </div>
                    ) : (
                      <div className="hidden group-hover:flex gap-1 text-[11px] text-slate-400">
                        <button
                          type="button"
                          className="hover:text-slate-700"
                          onClick={(e) => {
                            e.stopPropagation();
                            setDraftTitle(c.title);
                            setEditing(c.id);
                          }}
                        >
                          이름
                        </button>
                        <button
                          type="button"
                          className="hover:text-slate-700"
                          onClick={(e) => {
                            e.stopPropagation();
                            onArchive(c.id);
                          }}
                        >
                          보관
                        </button>
                      </div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        ))}
      </div>
    </div>
  );
}
