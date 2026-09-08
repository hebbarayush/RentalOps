import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Board, BoardMasthead, BoardSection, BoardState, Notice, StatusNotice } from "../components/board";
import { ApiError } from "../lib/api";
import { formatCurrency, formatDate } from "../lib/format";
import { leasesApi, maintenanceApi, paymentsApi, propertiesApi, tenantsApi } from "../lib/resources";
import { useCollection } from "../lib/useCollection";
import type { PaymentMethod, RentPaymentResponse } from "../types";

const DAY = 86_400_000;
const daysBetween = (iso: string) => Math.round((new Date(iso).getTime() - Date.now()) / DAY);
const firstName = (full?: string | null) => full?.trim().split(/\s+/)[0] ?? "there";

const METHODS: { value: PaymentMethod; label: string }[] = [
  { value: "UPI", label: "UPI" },
  { value: "BANK_TRANSFER", label: "Bank transfer" },
  { value: "CASH", label: "Cash" },
  { value: "CARD", label: "Card" },
  { value: "OTHER", label: "Other" }
];

/** One outstanding rent charge, with an inline "I've paid this" report flow. */
function ChargeRow({ charge, onReported }: { charge: RentPaymentResponse; onReported: () => void }) {
  const [open, setOpen] = useState(false);
  const [method, setMethod] = useState<PaymentMethod>("UPI");
  const [reference, setReference] = useState("");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const left = daysBetween(charge.dueDate);
  const dueLabel =
    left < 0 ? `${-left} day${left === -1 ? "" : "s"} overdue` : left === 0 ? "due today" : `due ${formatDate(charge.dueDate)}`;

  async function submit() {
    setBusy(true);
    setError("");
    try {
      await paymentsApi.reportPayment(charge.id, {
        paymentMethod: method,
        transactionReference: reference || null,
        note: note || null
      });
      onReported();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not report the payment");
      setBusy(false);
    }
  }

  return (
    <li className={`charge-row${left < 0 ? " charge-row--overdue" : ""}`}>
      <div className="charge-row-head">
        <span className="charge-row-amount">{formatCurrency(charge.amountDue)}</span>
        <span className="charge-row-due">{dueLabel}</span>
      </div>

      {charge.reportedPaidAt ? (
        <p className="charge-row-status">
          Reported {formatDate(charge.reportedPaidAt)} via{" "}
          {(charge.reportedMethod ?? "").toLowerCase().replace("_", " ")} — waiting for your manager
          to confirm.
        </p>
      ) : !open ? (
        <button className="board-btn board-btn--primary board-btn--sm" onClick={() => setOpen(true)}>
          I've paid this
        </button>
      ) : (
        <div className="report-form">
          <label className="field">
            <span className="field-label">How did you pay?</span>
            <select
              className="input"
              value={method}
              onChange={(e) => setMethod(e.target.value as PaymentMethod)}
            >
              {METHODS.map((m) => (
                <option key={m.value} value={m.value}>
                  {m.label}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            <span className="field-label">Reference (optional)</span>
            <input
              className="input"
              placeholder="UPI ref / transaction ID"
              value={reference}
              onChange={(e) => setReference(e.target.value)}
            />
          </label>
          <label className="field">
            <span className="field-label">Note (optional)</span>
            <input
              className="input"
              placeholder="Anything your manager should know"
              value={note}
              onChange={(e) => setNote(e.target.value)}
            />
          </label>
          {error && <p className="field-error">{error}</p>}
          <div className="card-actions">
            <button className="board-btn board-btn--primary" onClick={submit} disabled={busy}>
              {busy ? "Sending…" : "Send to manager"}
            </button>
            <button className="board-btn" onClick={() => setOpen(false)} disabled={busy}>
              Cancel
            </button>
          </div>
        </div>
      )}
    </li>
  );
}

export function TenantHomePage() {
  const { user } = useAuth();
  const navigate = useNavigate();

  const profile = useCollection(() => tenantsApi.me());
  const leases = useCollection(() => leasesApi.list());
  const payments = useCollection(() => paymentsApi.list({ size: 50, sort: "dueDate,desc" }));
  const properties = useCollection(() => propertiesApi.list({ size: 50 }));
  const work = useCollection(() => maintenanceApi.list({ size: 20, sort: "id,desc" }));

  const propertyName = useMemo(() => {
    const m = new Map<number, string>();
    properties.data?.content.forEach((p) => m.set(p.id, p.name));
    return m;
  }, [properties.data]);

  const notLinked = !profile.loading && !!profile.error;

  const leaseRows = leases.data?.content ?? [];
  const lease = leaseRows.find((l) => l.leaseStatus === "ACTIVE") ?? leaseRows[0];
  const home = lease ? propertyName.get(lease.propertyId) : undefined;
  const leaseEndsIn = lease ? daysBetween(lease.endDate) : Infinity;
  const leaseEndingSoon = lease?.leaseStatus === "ACTIVE" && leaseEndsIn <= 30;

  const paymentRows = payments.data?.content ?? [];
  const openCharges = paymentRows
    .filter((p) => p.paymentStatus !== "PAID")
    .sort((a, b) => a.dueDate.localeCompare(b.dueDate));
  const outstanding = openCharges.reduce((sum, p) => sum + (p.amountDue - p.amountPaid), 0);
  const nextCharge = openCharges[0];
  const overdue = !!nextCharge && daysBetween(nextCharge.dueDate) < 0;
  const reportedCount = openCharges.filter((c) => c.reportedPaidAt).length;

  const workRows = (work.data?.content ?? []).filter((w) => w.status !== "CLOSED");

  return (
    <Board>
      <BoardMasthead
        title={`Welcome back, ${firstName(user?.fullName)}`}
        standfirst={lease ? `Unit ${lease.unitNumber}${home ? `, ${home}` : ""}` : "Your tenancy"}
      />

      {notLinked ? (
        <div className="board-columns board-columns--solo">
          <div className="board-main">
            <div className="notice-strip">
              <Notice
                tone="warn"
                tab="Setup"
                heading="Your account isn't linked yet"
                meta="A property manager needs to add you before your lease and rent appear here."
              >
                <p className="notice-figure notice-figure--sm">{user?.email}</p>
                <p className="card-fine">Share this email with your manager so they can link your tenancy.</p>
              </Notice>
            </div>
          </div>
        </div>
      ) : (
        <div className="board-columns board-columns--portal">
          <div className="board-main">
            <BoardSection title="Your rent">
              <BoardState
                loading={payments.loading}
                error={payments.error}
                empty={paymentRows.length === 0}
                emptyMessage="No rent charges yet. They'll appear here once your lease is active."
              >
                <div className="notice-strip notice-strip--single">
                  <Notice
                    size="lg"
                    tone={overdue ? "urgent" : outstanding > 0 ? "warn" : "plain"}
                    tab={
                      overdue
                        ? `${-daysBetween(nextCharge!.dueDate)} days late`
                        : outstanding > 0
                          ? "Due"
                          : undefined
                    }
                    heading={outstanding > 0 ? "Rent to pay" : "Rent is up to date"}
                    meta={
                      outstanding > 0
                        ? `${openCharges.length} charge${openCharges.length === 1 ? "" : "s"} outstanding` +
                          (nextCharge ? ` · earliest due ${formatDate(nextCharge.dueDate)}` : "") +
                          (reportedCount > 0 ? ` · ${reportedCount} awaiting confirmation` : "")
                        : lease
                          ? `${formatCurrency(lease.monthlyRent)} a month`
                          : undefined
                    }
                    fine={
                      outstanding > 0
                        ? "Pay your manager directly — RentalOps records the payment, it doesn't collect it. Report each charge once you've paid it."
                        : "Every charge settled on or before its due date."
                    }
                  >
                    {outstanding > 0 && (
                      <>
                        <p className="notice-figure">
                          {formatCurrency(outstanding)}
                          <span className="notice-figure-part"> outstanding</span>
                        </p>
                        <ul className="charge-list">
                          {openCharges.map((c) => (
                            <ChargeRow key={c.id} charge={c} onReported={() => payments.reload()} />
                          ))}
                        </ul>
                      </>
                    )}
                  </Notice>
                </div>
              </BoardState>
            </BoardSection>

            <BoardSection title="Your lease">
              <BoardState loading={leases.loading} error={leases.error} empty={!lease} emptyMessage="No lease on file yet.">
                {lease && (
                  <div className="notice-strip notice-strip--single">
                    <Notice
                      tone={leaseEndingSoon ? "warn" : "plain"}
                      tab={leaseEndingSoon ? "Ending soon" : undefined}
                      heading={`Unit ${lease.unitNumber}${home ? ` · ${home}` : ""}`}
                      meta={`${formatDate(lease.startDate)} → ${formatDate(lease.endDate)}`}
                    >
                      <dl className="notice-facts">
                        <div>
                          <dt>Monthly rent</dt>
                          <dd>{formatCurrency(lease.monthlyRent)}</dd>
                        </div>
                        <div>
                          <dt>Deposit held</dt>
                          <dd>{formatCurrency(lease.securityDeposit)}</dd>
                        </div>
                        <div>
                          <dt>Status</dt>
                          <dd>{lease.leaseStatus.toLowerCase()}</dd>
                        </div>
                      </dl>
                    </Notice>
                  </div>
                )}
              </BoardState>
            </BoardSection>

            <BoardSection title="Maintenance">
              <div className="notice-strip notice-strip--single">
                <Notice
                  tone="plain"
                  size="sm"
                  heading="Something broken at home?"
                  meta="Raise it here and follow it through to resolved."
                  actions={
                    <button className="board-btn board-btn--primary" onClick={() => navigate("/maintenance")}>
                      Report a problem
                    </button>
                  }
                />
              </div>
            </BoardSection>
          </div>

          <aside className="board-aside">
            <BoardSection title="Open requests">
              <BoardState
                loading={work.loading}
                error={work.error}
                empty={workRows.length === 0}
                emptyMessage="Nothing open right now."
              >
                <div className="status-grid status-grid--stack">
                  {workRows.slice(0, 5).map((w) => (
                    <StatusNotice
                      key={w.id}
                      subject={w.title}
                      value={
                        <span className="status-notice-tag">{w.status.replace("_", " ").toLowerCase()}</span>
                      }
                      foot={w.triage?.category ? w.triage.category.replace(/_/g, " ").toLowerCase() : undefined}
                    />
                  ))}
                </div>
              </BoardState>
            </BoardSection>

            {paymentRows.length > 0 && (
              <BoardSection title="Recent rent">
                <div className="status-grid status-grid--stack">
                  {paymentRows.slice(0, 5).map((p) => (
                    <StatusNotice
                      key={p.id}
                      subject={formatCurrency(p.amountDue)}
                      value={
                        <span className="status-notice-tag">
                          {p.paymentStatus === "PAID"
                            ? `paid ${p.paidDate ? formatDate(p.paidDate) : ""}`.trim()
                            : p.reportedPaidAt
                              ? "reported · awaiting confirmation"
                              : p.paymentStatus.toLowerCase()}
                        </span>
                      }
                      foot={`due ${formatDate(p.dueDate)}`}
                    />
                  ))}
                </div>
              </BoardSection>
            )}
          </aside>
        </div>
      )}
    </Board>
  );
}
