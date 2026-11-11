import type { ButtonHTMLAttributes, InputHTMLAttributes } from "react";

export function Field({
  label,
  id,
  hint,
  ...input
}: InputHTMLAttributes<HTMLInputElement> & { label: string; id: string; hint?: string }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        name={id}
        className="h-11 rounded-lg border border-line bg-surface-2 px-3 text-base outline-none placeholder:text-muted/70 focus:border-live"
        {...input}
      />
      {hint && <p className="text-xs text-muted">{hint}</p>}
    </div>
  );
}

export function PrimaryButton({ className = "", ...props }: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      className={`h-11 rounded-lg bg-live px-4 font-semibold text-white transition-colors hover:bg-live-strong disabled:cursor-not-allowed disabled:opacity-60 ${className}`}
      {...props}
    />
  );
}

export function ErrorMessage({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <p role="alert" className="rounded-lg border border-danger/40 bg-danger/10 px-3 py-2 text-sm text-danger">
      {message}
    </p>
  );
}

export function Card({ children, className = "" }: { children: React.ReactNode; className?: string }) {
  return <div className={`rounded-2xl border border-line bg-surface p-6 sm:p-8 ${className}`}>{children}</div>;
}

/** 찾을 수 없음 · 권한 없음 · 오류처럼 화면 전체를 대신하는 안내 */
export function Notice({ title, description, children }: { title: string; description?: string; children?: React.ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-xl border border-line bg-surface px-6 py-16 text-center">
      <p className="text-lg font-semibold">{title}</p>
      {description && <p className="text-sm text-muted">{description}</p>}
      {children}
    </div>
  );
}
