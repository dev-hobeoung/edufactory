import clsx from 'clsx';
import { useEffect, useState, type ButtonHTMLAttributes, type ReactNode } from 'react';

type Variant = 'primary' | 'secondary' | 'text' | 'danger';
type Size = 'xs' | 'sm' | 'md';

const VARIANT: Record<Variant, string> = {
  primary: 'bg-indigo-600 hover:bg-indigo-700 text-white shadow-sm disabled:bg-indigo-300',
  secondary: 'bg-white hover:bg-slate-50 text-slate-700 border border-slate-300 shadow-sm disabled:text-slate-400',
  text: 'bg-transparent hover:bg-slate-100 text-slate-600 disabled:text-slate-300',
  danger: 'bg-red-600 hover:bg-red-700 text-white disabled:bg-red-300',
};

const SIZE: Record<Size, string> = {
  xs: 'px-2.5 py-1 text-xs rounded-md',
  sm: 'px-3 py-1.5 text-xs rounded-lg',
  md: 'px-4 py-2 text-sm rounded-lg',
};

export function Button({
  variant = 'secondary',
  size = 'sm',
  className,
  type = 'button',
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; size?: Size }) {
  return (
    <button
      type={type}
      className={clsx(
        'inline-flex items-center justify-center gap-1.5 font-medium transition whitespace-nowrap',
        'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-500',
        'disabled:cursor-not-allowed',
        VARIANT[variant],
        SIZE[size],
        className,
      )}
      {...rest}
    />
  );
}

export function Chip({ children, className, title }: { children: ReactNode; className?: string; title?: string }) {
  return (
    <span
      title={title}
      className={clsx(
        'inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold whitespace-nowrap',
        className ?? 'bg-slate-100 text-slate-600',
      )}
    >
      {children}
    </span>
  );
}

export function Card({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={clsx('bg-white border border-slate-200 rounded-lg', className)}>{children}</div>;
}

export function PageHeader({ title, sub, actions }: { title: string; sub?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="flex items-end justify-between gap-4 flex-wrap">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">{title}</h1>
        {sub && <div className="text-sm text-slate-500 mt-0.5">{sub}</div>}
      </div>
      {actions && <div className="flex gap-2 items-center">{actions}</div>}
    </div>
  );
}

export function Spinner({ className }: { className?: string }) {
  return (
    <span
      aria-hidden
      className={clsx(
        'inline-block w-4 h-4 border-2 border-current border-t-transparent rounded-full animate-spin',
        className,
      )}
    />
  );
}

export function Loading({ label = '불러오는 중…' }: { label?: string }) {
  return (
    <div className="flex items-center gap-2 text-sm text-slate-400 py-6 justify-center">
      <Spinner />
      {label}
    </div>
  );
}

export function ErrorBanner({ message, onRetry }: { message: string | null; onRetry?: () => void }) {
  if (!message) return null;
  return (
    <div className="flex items-center gap-3 text-sm text-red-700 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
      <span className="flex-1">{message}</span>
      {onRetry && (
        <Button size="xs" variant="text" onClick={onRetry}>
          다시 시도
        </Button>
      )}
    </div>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="text-sm text-slate-400 text-center py-10">{children}</div>;
}

export function Modal({
  open,
  onClose,
  title,
  children,
  width = 'max-w-2xl',
}: {
  open: boolean;
  onClose: () => void;
  title: ReactNode;
  children: ReactNode;
  width?: string;
}) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);
  if (!open) return null;
  return (
    <div
      className="fixed inset-0 z-50 bg-slate-900/40 flex items-center justify-center p-4"
      onMouseDown={(e) => e.target === e.currentTarget && onClose()}
    >
      <div
        role="dialog"
        aria-modal="true"
        className={clsx('w-full bg-white rounded-xl shadow-xl flex flex-col max-h-[85vh]', width)}
      >
        <div className="px-5 py-3 border-b border-slate-200 flex items-center gap-3">
          <div className="font-semibold text-slate-800 flex-1 min-w-0">{title}</div>
          <Button variant="text" size="xs" onClick={onClose} aria-label="닫기">
            닫기
          </Button>
        </div>
        <div className="overflow-y-auto p-5">{children}</div>
      </div>
    </div>
  );
}

export function Toggle({
  checked,
  onChange,
  label,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      onClick={() => onChange(!checked)}
      className={clsx(
        'relative inline-flex h-5 w-9 items-center rounded-full transition',
        checked ? 'bg-indigo-600' : 'bg-slate-300',
      )}
    >
      <span
        className={clsx(
          'inline-block h-4 w-4 rounded-full bg-white shadow transition',
          checked ? 'translate-x-[18px]' : 'translate-x-0.5',
        )}
      />
    </button>
  );
}

export function Segmented<T extends string>({
  value,
  options,
  onChange,
  label,
}: {
  value: T;
  options: { value: T; label: string }[];
  onChange: (v: T) => void;
  label: string;
}) {
  return (
    <div role="radiogroup" aria-label={label} className="inline-flex bg-slate-100 rounded-lg p-0.5 gap-0.5">
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          role="radio"
          aria-checked={value === o.value}
          onClick={() => onChange(o.value)}
          className={clsx(
            'px-3 py-1 text-xs font-medium rounded-md transition',
            value === o.value ? 'bg-white text-indigo-700 shadow-sm' : 'text-slate-500 hover:text-slate-700',
          )}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

/** 단계형 슬라이더 — 눈금마다 이름이 붙는다 (예: 창의성 0~4단계) */
export function Slider({
  value,
  onChange,
  steps,
  label,
}: {
  value: number;
  onChange: (v: number) => void;
  steps: string[];
  label: string;
}) {
  const max = steps.length - 1;
  return (
    <div className="space-y-1">
      <input
        type="range"
        min={0}
        max={max}
        step={1}
        value={value}
        aria-label={label}
        aria-valuetext={steps[value]}
        onChange={(e) => onChange(Number(e.target.value))}
        className="w-full accent-indigo-600"
      />
      <div className="flex justify-between">
        {steps.map((s, i) => (
          <button
            key={s}
            type="button"
            onClick={() => onChange(i)}
            className={clsx(
              'text-[10px] w-0 flex justify-center whitespace-nowrap',
              i === value ? 'text-indigo-700 font-semibold' : 'text-slate-400 hover:text-slate-600',
            )}
          >
            {s}
          </button>
        ))}
      </div>
    </div>
  );
}

export const inputClass =
  'border border-slate-300 rounded-lg px-3 py-1.5 text-sm bg-white focus:outline-none focus:ring-2 focus:ring-indigo-200 focus:border-indigo-400';

export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: ReactNode }) {
  return (
    <label className="block space-y-1">
      <span className="text-xs font-medium text-slate-500">{label}</span>
      {children}
      {hint && <span className="block text-[11px] text-slate-400">{hint}</span>}
    </label>
  );
}

/** 짧은 안내 토스트 (alert 대신) */
export function Toast({ message, tone = 'ok' }: { message: string | null; tone?: 'ok' | 'error' }) {
  if (!message) return null;
  return (
    <div
      role="status"
      className={clsx(
        'fixed bottom-5 left-1/2 -translate-x-1/2 z-50 px-4 py-2 rounded-lg shadow-lg text-sm',
        tone === 'ok' ? 'bg-slate-900 text-white' : 'bg-red-600 text-white',
      )}
    >
      {message}
    </div>
  );
}

export const textareaClass = `${inputClass} w-full resize-y leading-relaxed`;

/** 확인 대화 — window.confirm 대신 */
export function ConfirmButton({
  label,
  confirmLabel = '정말 삭제',
  onConfirm,
  size = 'xs',
  variant = 'text',
  disabled,
}: {
  label: ReactNode;
  confirmLabel?: string;
  onConfirm: () => void;
  size?: Size;
  variant?: Variant;
  disabled?: boolean;
}) {
  const [armed, setArmed] = useState(false);
  useEffect(() => {
    if (!armed) return;
    const t = setTimeout(() => setArmed(false), 3000);
    return () => clearTimeout(t);
  }, [armed]);
  return armed ? (
    <Button
      size={size}
      variant="danger"
      disabled={disabled}
      onClick={() => {
        setArmed(false);
        onConfirm();
      }}
    >
      {confirmLabel}
    </Button>
  ) : (
    <Button size={size} variant={variant} disabled={disabled} onClick={() => setArmed(true)}>
      {label}
    </Button>
  );
}

export function Stat({ label, value, sub }: { label: string; value: ReactNode; sub?: ReactNode }) {
  return (
    <div className="bg-white border border-slate-200 rounded-lg px-4 py-3">
      <div className="text-xs text-slate-500">{label}</div>
      <div className="text-xl font-semibold text-slate-800 tabular-nums mt-0.5">{value}</div>
      {sub && <div className="text-[11px] text-slate-400 mt-0.5">{sub}</div>}
    </div>
  );
}
