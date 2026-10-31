import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { api, errorMessage } from '../../api/client';
import { LabelList } from '../../components/LabelBadge';
import { Page } from '../../components/Layout';
import { Chip, Empty, ErrorBanner, Loading, PageHeader } from '../../components/ui';
import { num, pct, rateTone } from '../../lib/format';
import { StatusBadge } from '../materials/MaterialsPage';

export default function ExamsPage() {
  const navigate = useNavigate();
  const query = useQuery({ queryKey: ['exams'], queryFn: api.exams });
  const list = query.data ?? [];
  return (
    <Page wide>
      <PageHeader
        title="시험·성적"
        sub={
          <>
            시험 자료의 문항(배점·정답)에 성적을 넣으면 문항별 정답률·변별도·선택지 분포를 계산해 문제 제작의 난이도 근거로 씁니다.
            시험 자료는 <Link to="/materials" className="text-indigo-600 hover:underline">자료</Link>에서 "시험 자료"로 올리거나 지정합니다.
          </>
        }
      />
      <ErrorBanner message={query.error ? errorMessage(query.error) : null} onRetry={() => query.refetch()} />
      {query.isLoading ? (
        <Loading />
      ) : list.length === 0 ? (
        <Empty>시험 자료가 없습니다.</Empty>
      ) : (
        <div className="bg-white border border-slate-200 rounded-lg overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="bg-slate-50 text-xs text-slate-500">
              <tr>
                <th className="text-left px-3 py-2 font-medium">시험</th>
                <th className="text-left px-3 py-2 font-medium">라벨</th>
                <th className="text-right px-3 py-2 font-medium">문항</th>
                <th className="text-right px-3 py-2 font-medium">만점</th>
                <th className="text-right px-3 py-2 font-medium">성적 세트</th>
                <th className="text-right px-3 py-2 font-medium">응시</th>
                <th className="text-right px-3 py-2 font-medium">평균 득점률</th>
              </tr>
            </thead>
            <tbody>
              {list.map((e) => (
                <tr
                  key={e.material.id}
                  className="border-t border-slate-100 hover:bg-slate-50 cursor-pointer"
                  onClick={() => navigate(`/exams/${e.material.id}`)}
                >
                  <td className="px-3 py-2">
                    <div className="font-medium text-slate-800">{e.material.title}</div>
                    {e.material.status !== 'READY' && <StatusBadge status={e.material.status} />}
                  </td>
                  <td className="px-3 py-2">
                    <LabelList labels={e.material.labels} max={4} />
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums">{e.itemCount}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{num(e.maxTotal, 1)}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{e.scoreSetCount}</td>
                  <td className="px-3 py-2 text-right tabular-nums">{num(e.n)}</td>
                  <td className="px-3 py-2 text-right">
                    {e.meanRate === null ? <span className="text-slate-400">–</span> : <Chip className={rateTone(e.meanRate)}>{pct(e.meanRate)}</Chip>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Page>
  );
}
