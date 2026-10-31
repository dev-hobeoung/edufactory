import { useState } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { isLoggedIn, saveSession } from '../api/auth';
import { api, errorMessage } from '../api/client';
import { Button, ErrorBanner, inputClass } from '../components/ui';

export default function LoginPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const next = params.get('next');
  const target = next && next.startsWith('/') && !next.startsWith('/login') ? next : '/studio';
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (isLoggedIn()) return <Navigate to={target} replace />;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const res = await api.login(email.trim(), password);
      saveSession(res.token, res.user);
      navigate(target, { replace: true });
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="min-h-full flex items-center justify-center bg-slate-100 p-6">
      <form onSubmit={submit} className="w-full max-w-sm bg-white border border-slate-200 rounded-xl shadow-sm p-7 space-y-5">
        <div>
          <div className="text-xl font-bold text-slate-900 tracking-tight">EDU FACTORY</div>
          <div className="text-sm text-slate-500 mt-1">자료를 근거로 문제와 교육자료를 만드는 작업실</div>
        </div>
        <label className="block space-y-1">
          <span className="text-xs font-medium text-slate-500">이메일</span>
          <input
            type="email"
            autoComplete="username"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className={`${inputClass} w-full`}
          />
        </label>
        <label className="block space-y-1">
          <span className="text-xs font-medium text-slate-500">비밀번호</span>
          <input
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className={`${inputClass} w-full`}
          />
        </label>
        <ErrorBanner message={error} />
        <Button type="submit" variant="primary" size="md" className="w-full" disabled={busy}>
          {busy ? '로그인 중…' : '로그인'}
        </Button>
      </form>
    </div>
  );
}
