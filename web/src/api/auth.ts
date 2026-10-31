import type { User } from './types';

// 로그인 토큰·사용자를 localStorage 에 둔다. 401 이면 지우고 로그인 화면으로 보낸다.

const TOKEN_KEY = 'edufactory.token';
const USER_KEY = 'edufactory.user';

function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string | null) {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    // 저장소를 쓸 수 없는 환경
  }
}

export function getToken(): string | null {
  return read(TOKEN_KEY);
}

export function getUser(): User | null {
  const raw = read(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as User;
  } catch {
    return null;
  }
}

export function saveSession(token: string, user: User) {
  write(TOKEN_KEY, token);
  write(USER_KEY, JSON.stringify(user));
}

export function saveUser(user: User) {
  write(USER_KEY, JSON.stringify(user));
}

export function clearSession() {
  write(TOKEN_KEY, null);
  write(USER_KEY, null);
}

export function isLoggedIn(): boolean {
  return !!getToken();
}

/** 401 을 받으면 세션을 지우고 로그인 화면으로 보낸다 (돌아올 경로를 남긴다) */
export function handleUnauthorized() {
  clearSession();
  if (!window.location.pathname.startsWith('/login')) {
    const next = encodeURIComponent(window.location.pathname + window.location.search);
    window.location.assign(`/login?next=${next}`);
  }
}
