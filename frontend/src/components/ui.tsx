import { useEffect } from "react";
import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";
import { titleCase } from "../lib/format";

export function PageHeader({
  title,
  subtitle,
  actions
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}) {
  return (
    <div className="page-header">
      <div>
        <h2>{title}</h2>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {actions && <div className="page-header-actions">{actions}</div>}
    </div>
  );
}

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "danger" | "ghost";
};

export function Button({ variant = "primary", className = "", ...props }: ButtonProps) {
  return <button className={`btn btn-${variant} ${className}`.trim()} {...props} />;
}

export function Field({
  label,
  error,
  children
}: {
  label: string;
  error?: string;
  children: ReactNode;
}) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {error && <span className="field-error">{error}</span>}
    </label>
  );
}

export function TextInput(props: InputHTMLAttributes<HTMLInputElement>) {
  return <input className="input" {...props} />;
}

export function TextArea(props: TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return <textarea className="input" rows={3} {...props} />;
}

export function Select({
  options,
  ...props
}: SelectHTMLAttributes<HTMLSelectElement> & { options: readonly string[] }) {
  return (
    <select className="input" {...props}>
      {options.map((opt) => (
        <option key={opt} value={opt}>
          {titleCase(opt)}
        </option>
      ))}
    </select>
  );
}

const STATUS_TONE: Record<string, string> = {
  ACTIVE: "green",
  PAID: "green",
  RESOLVED: "green",
  EXCELLENT: "green",
  GOOD: "green",
  CLOSED: "grey",
  INACTIVE: "grey",
  DISABLED: "grey",
  TERMINATED: "grey",
  EXPIRED: "grey",
  NEW: "grey",
  DRAFT: "blue",
  PENDING: "amber",
  IN_PROGRESS: "blue",
  PARTIAL: "amber",
  OPEN: "amber",
  FAIR: "amber",
  OVERDUE: "red",
  FAILED: "red",
  POOR: "red",
  URGENT: "red",
  HIGH: "amber",
  MEDIUM: "blue",
  LOW: "grey"
};

export function StatusPill({ value }: { value: string }) {
  const tone = STATUS_TONE[value] ?? "grey";
  return <span className={`pill pill-${tone}`}>{titleCase(value)}</span>;
}

export function Modal({
  title,
  onClose,
  children
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === "Escape" && onClose();
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>{title}</h3>
          <button className="modal-close" onClick={onClose} aria-label="Close">
            ×
          </button>
        </div>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  );
}

export function DataState({
  loading,
  error,
  empty,
  emptyMessage = "Nothing here yet.",
  children
}: {
  loading: boolean;
  error?: string | null;
  empty: boolean;
  emptyMessage?: string;
  children: ReactNode;
}) {
  if (loading) return <div className="data-state">Loading…</div>;
  if (error) return <div className="data-state data-state-error">{error}</div>;
  if (empty) return <div className="data-state">{emptyMessage}</div>;
  return <>{children}</>;
}

export function Pagination({
  page,
  totalPages,
  totalElements,
  onPage
}: {
  page: number;
  totalPages: number;
  totalElements: number;
  onPage: (p: number) => void;
}) {
  if (totalElements === 0) return null;
  return (
    <div className="pagination">
      <span>
        {totalElements} result{totalElements === 1 ? "" : "s"}
        {totalPages > 1 ? ` · page ${page + 1} of ${totalPages}` : ""}
      </span>
      {totalPages > 1 && (
        <div className="pagination-buttons">
          <button className="btn btn-secondary" disabled={page === 0} onClick={() => onPage(page - 1)}>
            Prev
          </button>
          <button
            className="btn btn-secondary"
            disabled={page >= totalPages - 1}
            onClick={() => onPage(page + 1)}
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}

export function FilterBar({ children }: { children: ReactNode }) {
  return <div className="filter-bar">{children}</div>;
}

export function Table({ columns, children }: { columns: string[]; children: ReactNode }) {
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c}>{c}</th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}
