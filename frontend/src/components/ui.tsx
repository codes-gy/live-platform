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
