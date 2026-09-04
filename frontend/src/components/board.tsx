import type { ReactNode } from "react";

/**
 * Shared building blocks for RentalOps' dashboards. Calm, light, Stripe/Linear register — the
 * chrome recedes so the figures and the "needs attention" items lead. See the surface brief at
 * .impeccable/surfaces/frontend-src-pages-dashboardpage-tsx.md for the direction contract.
 */

export function Board({ children }: { children: ReactNode }) {
  return <div className="board">{children}</div>;
}

export function BoardMasthead({
  title,
  standfirst,
  aside
}: {
  title: string;
  standfirst?: string;
  aside?: ReactNode;
}) {
  return (
    <header className="board-head">
      <div>
        <h1>{title}</h1>
        {standfirst && <p>{standfirst}</p>}
      </div>
      {aside && <div className="board-head-aside">{aside}</div>}
    </header>
  );
}

export function BoardSection({
  title,
  note,
  children
}: {
  title: string;
  note?: string;
  children: ReactNode;
}) {
  return (
    <section className="board-section">
      <div className="board-section-head">
        <h2>{title}</h2>
        {note && <p>{note}</p>}
      </div>
      {children}
    </section>
  );
}

type Tone = "urgent" | "warn" | "plain";

export function Notice({
  tone = "plain",
  tab,
  heading,
  meta,
  children,
  fine,
  actions,
  clearing = false,
  size = "md"
}: {
  tone?: Tone;
  /** short status word rendered as a chip in the card header, e.g. "3 days late" */
  tab?: string;
  heading: ReactNode;
  meta?: ReactNode;
  children?: ReactNode;
  fine?: ReactNode;
  actions?: ReactNode;
  clearing?: boolean;
  size?: "sm" | "md" | "lg";
}) {
  return (
    <article
      className={["card", `card--${tone}`, `card--${size}`, clearing ? "is-clearing" : ""]
        .join(" ")
        .trim()}
    >
      <div className="card-top">
        <h3>{heading}</h3>
        {tab && (
          <span className={`chip chip--${tone}`}>{tab}</span>
        )}
      </div>
      {meta && <p className="card-meta">{meta}</p>}
      {children && <div className="card-body">{children}</div>}
      {fine && <p className="card-fine">{fine}</p>}
      {actions && <div className="card-actions">{actions}</div>}
    </article>
  );
}

/** A compact stat card for secondary/aside content. */
export function StatusNotice({
  subject,
  value,
  foot,
  slip
}: {
  subject: string;
  value: ReactNode;
  foot?: ReactNode;
  slip?: ReactNode;
}) {
  return (
    <article className="ministat">
      <span className="ministat-label">{subject}</span>
      <span className="ministat-value">{value}</span>
      {foot && <span className="ministat-foot">{foot}</span>}
      {slip && <div className="status-notice-slip">{slip}</div>}
    </article>
  );
}

/** The routine portfolio position — one quiet card, label/value rows with hairline dividers. */
export function Ledger({ children }: { children: ReactNode }) {
  return <div className="ledger">{children}</div>;
}

export function LedgerRow({
  label,
  value,
  note,
  slip
}: {
  label: string;
  value: ReactNode;
  note?: ReactNode;
  slip?: ReactNode;
}) {
  return (
    <div className="ledger-row">
      <span className="ledger-label">{label}</span>
      <span className="ledger-value">{value}</span>
      {note && <span className="ledger-note">{note}</span>}
      {slip}
    </div>
  );
}

export type TrendPoint = { label: string; value: number };

export function trimHistory(points: TrendPoint[]): TrendPoint[] {
  const first = points.findIndex((p) => p.value > 0);
  return first <= 0 ? points : points.slice(first);
}

export function hasHistory(points: TrendPoint[]): boolean {
  return points.filter((p) => p.value > 0).length >= 3;
}

/** A small month-by-month bar strip; the current month reads in the accent colour. */
export function TrendSlip({
  points,
  caption,
  format
}: {
  points: TrendPoint[];
  caption: string;
  format?: (n: number) => string;
}) {
  const trimmed = trimHistory(points);
  const max = Math.max(1, ...trimmed.map((p) => p.value));
  const fmt = format ?? ((n: number) => String(n));
  const title = trimmed.map((p) => `${p.label}: ${fmt(p.value)}`).join("   ");
  return (
    <div className="trend-slip" title={title}>
      <div className="trend-bars" aria-hidden="true">
        {trimmed.map((p, i) => (
          <span className="trend-col" key={p.label}>
            <span className="trend-track">
              <span
                className={`trend-bar${i === trimmed.length - 1 ? " is-current" : ""}`}
                style={{ height: `${Math.max((p.value / max) * 100, p.value > 0 ? 12 : 4)}%` }}
              />
            </span>
          </span>
        ))}
      </div>
      <span className="trend-caption">
        {trimmed[0].label}–{trimmed[trimmed.length - 1].label} · {caption}
      </span>
    </div>
  );
}

/** What you've dealt with this session — a quiet trailing list, hidden until the first item. */
export function Counterfoil({
  items
}: {
  items: { id: string; label: string; sub?: string; time: string }[];
}) {
  if (items.length === 0) return null;
  return (
    <section className="counterfoil" aria-label="Cleared today">
      <h2>Cleared today</h2>
      <ul>
        {items.map((it) => (
          <li key={it.id}>
            <span className="counterfoil-stamp" aria-hidden="true">
              Done
            </span>
            <span className="counterfoil-label">{it.label}</span>
            {it.sub && <span className="counterfoil-sub">· {it.sub}</span>}
            <time className="counterfoil-time">{it.time}</time>
          </li>
        ))}
      </ul>
    </section>
  );
}

export function BoardState({
  loading,
  error,
  empty,
  emptyMessage = "Nothing here.",
  children
}: {
  loading: boolean;
  error?: string | null;
  empty?: boolean;
  emptyMessage?: string;
  children: ReactNode;
}) {
  if (loading)
    return (
      <div className="board-note">
        <span className="board-note-skeleton" />
        Loading…
      </div>
    );
  if (error) return <div className="board-note board-note--error">{error}</div>;
  if (empty) return <div className="board-note board-note--clear">{emptyMessage}</div>;
  return <>{children}</>;
}
