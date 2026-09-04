import { useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Board, BoardMasthead, BoardSection, BoardState, Notice, StatusNotice } from "../components/board";
import { formatCurrency, formatDate } from "../lib/format";
import { leasesApi, maintenanceApi, paymentsApi, propertiesApi, tenantsApi } from "../lib/resources";
import { useCollection } from "../lib/useCollection";

const DAY = 86_400_000;
const daysBetween = (iso: string) => Math.round((new Date(iso).getTime() - Date.now()) / DAY);
const firstName = (full?: string | null) => full?.trim().split(/\s+/)[0] ?? "there";

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
                      nextCharge
                        ? `Next charge ${formatCurrency(nextCharge.amountDue)} · due ${formatDate(nextCharge.dueDate)}`
                        : lease
                          ? `${formatCurrency(lease.monthlyRent)} a month`
                          : undefined
                    }
                    fine={
                      outstanding > 0
                        ? "Pay your manager directly — RentalOps records the payment, it doesn't collect it."
                        : "Every charge settled on or before its due date."
                    }
                  >
                    {outstanding > 0 && (
                      <p className="notice-figure">
                        {formatCurrency(outstanding)}
                        <span className="notice-figure-part"> outstanding</span>
                      </p>
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
