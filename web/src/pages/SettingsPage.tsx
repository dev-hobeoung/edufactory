import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { getUser } from '../api/auth';
import { api, errorMessage } from '../api/client';
import type { Role, Settings, UserInput } from '../api/types';
import { Bars } from '../components/Histogram';
import { Page } from '../components/Layout';
import { Button, Card, Chip, ConfirmButton, ErrorBanner, Field, Loading, PageHeader, Stat, inputClass } from '../components/ui';
import { num, usd } from '../lib/format';
import { useToast } from '../lib/toast';

const PURPOSE_LABEL: Record<string, string> = { chat: '대화 생성', structure: '자료 구조화', embedding: '임베딩' };

function SettingsForm() {
  const qc = useQueryClient();
  const toast = useToast();
  const query = useQuery({ queryKey: ['settings'], queryFn: api.settings });
  const [f, setF] = useState<Settings | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    if (query.data) setF(query.data);
  }, [query.data]);
  const save = useMutation({
    mutationFn: (s: Settings) => api.saveSettings(s),
    onSuccess: (s) => {
      qc.setQueryData(['settings'], s);
      toast('설정을 저장했습니다');
      setError(null);
    },
    onError: (e) => setError(errorMessage(e)),
  });
  if (!f) return query.isLoading ? <Loading /> : <ErrorBanner message={query.error ? errorMessage(query.error) : null} />;
  const dirty = JSON.stringify(f) !== JSON.stringify(query.data);
  return (
    <Card className="p-4 space-y-3">
      <div className="font-semibold text-slate-700">생성 설정</div>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
        <Field label="모델">
          <select value={f.model} onChange={(e) => setF({ ...f, model: e.target.value as Settings['model'] })} className={`${inputClass} w-full`}>
            <option value="claude-opus-5-5">Claude Opus 5.5</option>
            <option value="claude-sonnet-5-5">Claude Sonnet 5.5</option>
          </select>
        </Field>
        <Field label="추론 강도 (effort)">
          <select value={f.effort} onChange={(e) => setF({ ...f, effort: e.target.value as Settings['effort'] })} className={`${inputClass} w-full`}>
            {(['low', 'medium', 'high', 'xhigh'] as const).map((x) => (
              <option key={x} value={x}>
                {x}
              </option>
            ))}
          </select>
        </Field>
        <Field label="유사도 경고 기준" hint="기존 자료와 이 이상 비슷하면 경고 (0~1)">
          <input
            type="number"
            step="0.01"
            min={0.5}
            max={1}
            value={f.similarityWarn}
            onChange={(e) => setF({ ...f, similarityWarn: Number(e.target.value) })}
            className={`${inputClass} w-full`}
          />
        </Field>
        <Field label="요청당 도구 호출 한도">
          <input
            type="number"
            min={1}
            max={100}
            value={f.maxToolCalls}
            onChange={(e) => setF({ ...f, maxToolCalls: Number(e.target.value) || 1 })}
            className={`${inputClass} w-full`}
          />
        </Field>
      </div>
      <div className="text-xs text-slate-500">월 예산 {usd(f.monthlyBudgetUsd, 0)} (서버 환경변수로 정합니다)</div>
      <ErrorBanner message={error} />
      <div className="flex justify-end">
        <Button variant="primary" disabled={!dirty || save.isPending} onClick={() => save.mutate(f)}>
          저장
        </Button>
      </div>
    </Card>
  );
}

function UsageCard() {
  const query = useQuery({ queryKey: ['usage'], queryFn: api.usage });
  const u = query.data;
  if (!u) return query.isLoading ? <Loading /> : <ErrorBanner message={query.error ? errorMessage(query.error) : null} />;
  const ratio = u.monthBudgetUsd > 0 ? u.monthCostUsd / u.monthBudgetUsd : 0;
  return (
    <Card className="p-4 space-y-3">
      <div className="font-semibold text-slate-700">사용량</div>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
        <Stat label="이번 달 비용" value={usd(u.monthCostUsd)} sub={`예산 ${usd(u.monthBudgetUsd, 0)} 중 ${Math.round(ratio * 100)}%`} />
        {u.byPurpose.map((p) => (
          <Stat key={p.purpose} label={PURPOSE_LABEL[p.purpose] ?? p.purpose} value={usd(p.costUsd)} />
        ))}
      </div>
      {u.daily.length > 0 && (
        <div>
          <div className="text-xs text-slate-500 mb-1">일별 비용</div>
          <Bars data={u.daily.map((d) => ({ label: d.day, value: d.costUsd }))} format={(v) => usd(v)} />
        </div>
      )}
    </Card>
  );
}

function StatusCard() {
  const query = useQuery({ queryKey: ['system-status'], queryFn: api.systemStatus });
  const s = query.data;
  if (!s) return query.isLoading ? <Loading /> : <ErrorBanner message={query.error ? errorMessage(query.error) : null} />;
  const ok = (v: boolean, yes: string, no: string) => <Chip className={v ? 'bg-emerald-50 text-emerald-700' : 'bg-red-50 text-red-700'}>{v ? yes : no}</Chip>;
  return (
    <Card className="p-4 space-y-3">
      <div className="font-semibold text-slate-700">시스템 상태</div>
      <div className="grid md:grid-cols-3 gap-3 text-sm">
        <div className="space-y-1">
          <div className="text-xs text-slate-500">데이터베이스</div>
          {ok(s.db.ok, '정상', '오류')}
          {s.db.error && <div className="text-xs text-red-600">{s.db.error}</div>}
        </div>
        <div className="space-y-1">
          <div className="text-xs text-slate-500">Claude</div>
          {ok(s.claude.configured, '연결됨', 'API 키 없음')}
          <div className="text-xs text-slate-500">{s.claude.model}</div>
          {!s.claude.configured && <div className="text-xs text-amber-700">키가 없으면 대화 생성이 안 되고 자료는 규칙 분할로 처리됩니다.</div>}
        </div>
        <div className="space-y-1">
          <div className="text-xs text-slate-500">임베딩</div>
          {ok(s.embedding.provider === 'voyage', 'Voyage', '로컬 (개발용)')}
          <div className="text-xs text-slate-500">
            {s.embedding.model} · {s.embedding.dimension}차원
          </div>
        </div>
      </div>
      <div className="grid grid-cols-3 md:grid-cols-7 gap-2">
        <Stat label="자료" value={num(s.counts.materials)} />
        <Stat label="청크" value={num(s.counts.chunks)} />
        <Stat label="임베딩" value={num(s.counts.embedded)} />
        <Stat label="라벨" value={num(s.counts.labels)} />
        <Stat label="규칙" value={num(s.counts.rules)} />
        <Stat label="산출물" value={num(s.counts.documents)} />
        <Stat label="성적 세트" value={num(s.counts.scoreSets)} />
      </div>
    </Card>
  );
}

function UsersCard() {
  const qc = useQueryClient();
  const me = getUser();
  const query = useQuery({ queryKey: ['users'], queryFn: api.users });
  const [f, setF] = useState<UserInput>({ email: '', name: '', password: '', role: 'EDITOR' });
  const [error, setError] = useState<string | null>(null);
  const create = useMutation({
    mutationFn: (u: UserInput) => api.createUser(u),
    onSuccess: () => {
      setF({ email: '', name: '', password: '', role: 'EDITOR' });
      setError(null);
      qc.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (e) => setError(errorMessage(e)),
  });
  const remove = useMutation({
    mutationFn: (id: string) => api.deleteUser(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
    onError: (e) => setError(errorMessage(e)),
  });
  const valid = f.email.includes('@') && f.name.trim() && f.password.length >= 8;
  return (
    <Card className="p-4 space-y-3">
      <div className="font-semibold text-slate-700">사용자 (관리자 전용)</div>
      {query.isLoading ? (
        <Loading />
      ) : (
        <table className="w-full text-sm">
          <tbody>
            {(query.data ?? []).map((u) => (
              <tr key={u.id} className="border-t border-slate-100">
                <td className="py-1.5 font-medium">{u.name}</td>
                <td className="py-1.5 text-slate-500">{u.email}</td>
                <td className="py-1.5">
                  <Chip className={u.role === 'ADMIN' ? 'bg-indigo-50 text-indigo-700' : 'bg-slate-100 text-slate-600'}>{u.role === 'ADMIN' ? '관리자' : '편집자'}</Chip>
                </td>
                <td className="py-1.5 text-right">{u.id !== me?.id && <ConfirmButton label="삭제" onConfirm={() => remove.mutate(u.id)} />}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <div className="flex flex-wrap gap-2 items-end">
        <input value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} placeholder="이름" aria-label="이름" className={`${inputClass} w-32`} />
        <input value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} placeholder="이메일" aria-label="이메일" className={`${inputClass} w-56`} />
        <input
          type="password"
          value={f.password}
          onChange={(e) => setF({ ...f, password: e.target.value })}
          placeholder="비밀번호 (8자 이상)"
          aria-label="비밀번호"
          className={`${inputClass} w-44`}
        />
        <select value={f.role} onChange={(e) => setF({ ...f, role: e.target.value as Role })} aria-label="역할" className={inputClass}>
          <option value="EDITOR">편집자</option>
          <option value="ADMIN">관리자</option>
        </select>
        <Button variant="primary" disabled={!valid || create.isPending} onClick={() => create.mutate({ ...f, name: f.name.trim(), email: f.email.trim() })}>
          추가
        </Button>
      </div>
      <ErrorBanner message={error} />
    </Card>
  );
}

export default function SettingsPage() {
  const me = getUser();
  return (
    <Page>
      <PageHeader title="설정" sub={me ? `${me.name} (${me.email})` : undefined} />
      <SettingsForm />
      <UsageCard />
      <StatusCard />
      {me?.role === 'ADMIN' && <UsersCard />}
    </Page>
  );
}
