import { useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import type { ReactNode } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { clearSession, getUser } from '../api/auth';
import { api } from '../api/client';
import { usd } from '../lib/format';

const NAV = [
  { to: '/studio', label: '작업실' },
  { to: '/documents', label: '산출물' },
  { to: '/materials', label: '자료' },
  { to: '/labels', label: '라벨' },
  { to: '/exams', label: '시험·성적' },
  { to: '/rules', label: '컨텍스트 규칙' },
  { to: '/settings', label: '설정' },
];

/** 대화가 끝나면 비용을 다시 읽는다 */
export function useRefreshUsage() {
  const qc = useQueryClient();
  return () => qc.invalidateQueries({ queryKey: ['usage'] });
}

export function Layout() {
  const navigate = useNavigate();
  const user = getUser();
  const { data: usage } = useQuery({ queryKey: ['usage'], queryFn: api.usage, refetchInterval: 5 * 60_000 });
  const ratio = usage && usage.monthBudgetUsd > 0 ? Math.min(1, usage.monthCostUsd / usage.monthBudgetUsd) : 0;

  const logout = () => {
    api.logout().catch(() => undefined);
    clearSession();
    navigate('/login', { replace: true });
  };

  return (
    <div className="flex h-full min-h-0">
      <aside className="no-print w-52 shrink-0 bg-slate-900 text-slate-300 flex flex-col">
        <div className="px-5 py-4 border-b border-slate-800">
          <div className="text-white font-bold tracking-tight">EDU FACTORY</div>
          <div className="text-[11px] text-slate-500 mt-0.5">문제·교육자료 제작</div>
        </div>
        <nav className="p-3 space-y-1 text-sm">
          {NAV.map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              className={({ isActive }) =>
                clsx('block px-3 py-2 rounded-lg transition', isActive ? 'bg-slate-800 text-white font-semibold' : 'hover:bg-slate-800/60')
              }
            >
              {n.label}
            </NavLink>
          ))}
        </nav>
        <div className="mt-auto p-4 border-t border-slate-800 text-xs space-y-2">
          <div className="flex justify-between gap-2">
            <span>이번 달 AI 비용</span>
            <span className="text-white tabular-nums">
              {usage ? `${usd(usage.monthCostUsd)} / ${usd(usage.monthBudgetUsd, 0)}` : '–'}
            </span>
          </div>
          <div
            className="h-1.5 bg-slate-800 rounded-full overflow-hidden"
            role="meter"
            aria-label="이번 달 AI 비용"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={Math.round(ratio * 100)}
          >
            <div className={clsx('h-full', ratio >= 0.9 ? 'bg-red-400' : 'bg-indigo-400')} style={{ width: `${ratio * 100}%` }} />
          </div>
          <div className="pt-2 text-slate-500 flex justify-between gap-2">
            <span className="truncate" title={user?.email}>
              {user?.name ?? '사용자'}
            </span>
            <button type="button" onClick={logout} className="hover:text-white shrink-0">
              로그아웃
            </button>
          </div>
        </div>
      </aside>
      <main className="flex-1 min-w-0 min-h-0 flex flex-col">
        <Outlet />
      </main>
    </div>
  );
}

/** 일반 화면(작업실 제외)의 스크롤 영역 */
export function Page({ children, wide = false }: { children: ReactNode; wide?: boolean }) {
  return (
    <div className="flex-1 min-h-0 overflow-y-auto">
      <div className={clsx('px-6 py-5 space-y-4', wide ? 'max-w-[1600px]' : 'max-w-[1280px]')}>{children}</div>
    </div>
  );
}
