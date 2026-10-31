import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react';
import { Toast } from '../components/ui';

type Notify = (message: string, tone?: 'ok' | 'error') => void;

const ToastContext = createContext<Notify>(() => undefined);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<{ message: string; tone: 'ok' | 'error' } | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>();
  const notify = useCallback<Notify>((message, tone = 'ok') => {
    setState({ message, tone });
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setState(null), 2800);
  }, []);
  return (
    <ToastContext.Provider value={notify}>
      {children}
      <Toast message={state?.message ?? null} tone={state?.tone} />
    </ToastContext.Provider>
  );
}

export const useToast = () => useContext(ToastContext);
