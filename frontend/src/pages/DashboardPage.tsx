import { useCallback, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Board,
  BoardMasthead,
  BoardSection,
  BoardState,
  Counterfoil,
  hasHistory,
  Ledger,
  LedgerRow,
  Notice,
  TrendSlip
} from "../components/board";
import { formatCurrency } from "../lib/format";
import {
  dashboardApi,
  leasesApi,
  maintenanceApi,
  paymentsApi,
  propertiesApi,
  tenantsApi
} from "../lib/resources";
import { useCollection } from "../lib/useCollection";
import type { MaintenanceResponse, ReliabilityResponse, RentPaymentResponse } from "../types";

const DAY = 86_400_000;

type Cleared = { id: string; label: string; sub?: string; time: string };

function readSnoozed(): Record<string, number> {
  try {
    return JSON.parse(localStorage.getItem("rentalops.board.snoozed") ?? "{}");
  } catch {
    return {};
  }
}
function writeSnoozed(next: Record<string, number>) {
  try {
    localStorage.setItem("rentalops.board.snoozed", JSON.stringify(next));
  } catch {
    /* private mode — snooze is best-effort */
  }
}

function daysBetween(iso: string): number {
  return Math.round((new Date(iso).getTime() - Date.now()) / DAY);
}
function nowTime(): string {
  return new Date().toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" });
}
function costLabel(band?: string | null): string | null {
  if (!band) return null;
  return `est. ${band.replace(/^Rs\s*/i, "₹").replace(/\s*-\s*/, "–")}`;
}

export function DashboardPage() {
  const navigate = useNavigate();

  const summary = useCollection(() => dashboardApi.summary());
  const risk = useCollection(() => dashboardApi.rentAtRisk());
  const trends = useCollection(() => dashboardApi.trends());
  const expiring = useCollection(() => leasesApi.expiringSoon());
  const openWork = useCollection(() => maintenanceApi.list({ status: "OPEN", size: 50, sort: "id,desc" }));
  const unpaid = useCollection(() => paymentsApi.list({ unpaidOnly: true, size: 100, sort: "dueDate,asc" }));
  const properties = useCollection(() => propertiesApi.list({ size: 200 }));
  const tenants = useCollection(() => tenantsApi.list({ size: 200 }));

  const [cleared, setCleared] = useState<Cleared[]>([]);
  const [clearing, setClearing] = useState<Set<string>>(new Set());
  const [snoozed, setSnoozed] = useState<Record<string, number>>(readSnoozed);

  const propertyName = useMemo(() => {
    const m = new Map<number, string>();
    properties.data?.content.forEach((p) => m.set(p.id, p.name));
    return m;
  }, [properties.data]);
  const tenantName = useMemo(() => {
    const m = new Map<number, string>();
    tenants.data?.content.forEach((t) => m.set(t.id, t.fullName));
    return m;
  }, [tenants.data]);
  const reasonFor = useMemo(() => {
    const m = new Map<number, ReliabilityResponse>();
    risk.data?.tenants.forEach((t) => m.set(t.tenantId, t));
    return m;
  }, [risk.data]);

  const isSnoozed = useCallback(
    (key: string) => {
      const until = snoozed[key];
      return typeof until === "number" && until > Date.now();
    },
    [snoozed]
  );

  const clear = useCallback(
    async (key: string, item: Cleared, action?: () => Promise<unknown>, reload?: () => void) => {
      if (action) {
        try {
          await action();
        } catch {
          return; // leave the notice on the board if the action failed
        }
      }
      setClearing((s) => new Set(s).add(key));
      window.setTimeout(() => {
        setClearing((s) => {
          const n = new Set(s);
          n.delete(key);
          return n;
        });
        setCleared((c) => [item, ...c]);
        reload?.();
      }, 260);
    },
    []
  );

  const snooze = useCallback((key: string) => {
    setSnoozed((prev) => {
      const next = { ...prev, [key]: Date.now() + DAY };
      writeSnoozed(next);
      return next;
    });
  }, []);

  // --- derive the board -------------------------------------------------------
  const unpaidRows: RentPaymentResponse[] = unpaid.data?.content ?? [];
  const overdue = unpaidRows.filter(
    (p) => p.paymentStatus === "OVERDUE" || (p.paymentStatus === "PENDING" && daysBetween(p.dueDate) < 0)
  );
  const dueSoon = unpaidRows.filter((p) => {
    const d = daysBetween(p.dueDate);
    return p.paymentStatus !== "OVERDUE" && d >= 0 && d <= 7;
  });

  const expiringRows = (expiring.data ?? []).filter((l) => !isSnoozed(`lease-${l.id}`));
  const expiringThisWeek = expiringRows.filter((l) => daysBetween(l.endDate) <= 7);
  const expiringLater = expiringRows.filter((l) => daysBetween(l.endDate) > 7);

  const workRows: MaintenanceResponse[] = openWork.data?.content ?? [];
  const priorityOf = (w: MaintenanceResponse) => w.triage?.suggestedPriority ?? w.priority;
  // Only genuinely urgent maintenance earns a place in the decision list.
  const urgentWork = workRows.filter((w) => priorityOf(w) === "URGENT");
  const routineWork = workRows.filter((w) => priorityOf(w) !== "URGENT");

  const loadingUrgent = unpaid.loading || openWork.loading || expiring.loading;
  const errorUrgent = unpaid.error ?? openWork.error ?? expiring.error;

  // Keep the attention list short: the lead item plus at most three more.
  const MAX_ATTENTION = 4;
  const showOverdue = overdue.slice(0, MAX_ATTENTION);
  const showWork = urgentWork.slice(0, Math.max(0, MAX_ATTENTION - showOverdue.length));
  const showLease = expiringThisWeek.slice(
    0,
    Math.max(0, MAX_ATTENTION - showOverdue.length - showWork.length)
  );
  const attentionShown = showOverdue.length + showWork.length + showLease.length;
  const attentionTotal = overdue.length + urgentWork.length + expiringThisWeek.length;
  const attentionOverflow = attentionTotal - attentionShown;

  // The single most pressing item is posted largest.
  const leadKey = overdue[0]
    ? `pay-${overdue[0].id}`
    : urgentWork[0]
      ? `work-${urgentWork[0].id}`
      : expiringThisWeek[0]
        ? `lease-${expiringThisWeek[0].id}`
        : null;

  // --- routine figures + trend slips ---------------------------------------
  const s = summary.data;
  const t = trends.data;
  const inCurrency = (n: number) => formatCurrency(n);
  const monthShort = (iso: string) => {
    const [y, m] = iso.split("-").map(Number);
    return new Date(y, m - 1, 1).toLocaleDateString("en-IN", { month: "short" });
  };
  const occPts = (t?.occupancy ?? []).map((o) => ({ label: monthShort(o.month), value: o.unitsUnderLease }));
  const colPts = (t?.collection ?? []).map((c) => ({ label: monthShort(c.month), value: c.collected }));
  const wkPts = (t?.maintenance ?? []).map((m) => ({ label: monthShort(m.month), value: m.opened }));

  return (
    <Board>
      <BoardMasthead
        title="Dashboard"
        standfirst={new Date().toLocaleDateString("en-IN", {
          weekday: "long",
          day: "numeric",
          month: "long",
          year: "numeric"
        })}
      />

      <div className="board-flow">
        <div className="board-main">
          <BoardSection
            title="Needs your attention"
            note={
              attentionOverflow > 0
                ? `Showing the ${attentionShown} most pressing · ${attentionOverflow} more below`
                : risk.data && risk.data.tenantsAtRisk > 0
                  ? `${risk.data.tenantsAtRisk} tenants at risk · ${formatCurrency(risk.data.exposureAmount)} outstanding exposure`
                  : undefined
            }
          >
            <BoardState
              loading={loadingUrgent}
              error={errorUrgent}
              empty={attentionTotal === 0}
              emptyMessage="Nothing needs you right now. Rent is current, no leases end this week, and no maintenance is waiting."
            >
              <div className="notice-strip">
                {showOverdue.map((p) => {
                  const key = `pay-${p.id}`;
                  const who = tenantName.get(p.tenantId) ?? `Tenant #${p.tenantId}`;
                  const reason = reasonFor.get(p.tenantId);
                  const late = -daysBetween(p.dueDate);
                  return (
                    <Notice
                      key={key}
                      tone="urgent"
                      size={key === leadKey ? "lg" : "md"}
                      tab={`${late} day${late === 1 ? "" : "s"} late`}
                      heading={`Rent overdue — ${who}`}
                      meta={`${propertyName.get(p.propertyId) ?? "Property"} · due ${new Date(p.dueDate).toLocaleDateString("en-IN", { day: "numeric", month: "short" })}`}
                      clearing={clearing.has(key)}
                      fine={
                        reason && reason.reasons?.length
                          ? `${reason.band[0] + reason.band.slice(1).toLowerCase()} payer, ${reason.score}/100 — ${reason.reasons[0]}`
                          : undefined
                      }
                      actions={
                        <>
                          <button
                            className="board-btn board-btn--primary"
                            onClick={() =>
                              clear(
                                key,
                                {
                                  id: key,
                                  label: `Received ${formatCurrency(p.amountDue)}`,
                                  sub: who,
                                  time: nowTime()
                                },
                                () =>
                                  paymentsApi.markPaid(p.id, {
                                    amountPaid: p.amountDue,
                                    paymentMethod: "UPI"
                                  }),
                                () => {
                                  unpaid.reload();
                                  summary.reload();
                                  risk.reload();
                                }
                              )
                            }
                          >
                            Mark received · {formatCurrency(p.amountDue)}
                          </button>
                          <button className="board-btn" onClick={() => navigate("/payments")}>
                            Open in rent
                          </button>
                        </>
                      }
                    >
                      <p className="notice-figure">
                        {formatCurrency(p.amountDue)}
                        {p.amountPaid > 0 && (
                          <span className="notice-figure-part"> · {formatCurrency(p.amountPaid)} part-paid</span>
                        )}
                      </p>
                    </Notice>
                  );
                })}

                {showLease.map((l) => {
                  const key = `lease-${l.id}`;
                  const left = daysBetween(l.endDate);
                  return (
                    <Notice
                      key={key}
                      tone="warn"
                      size={key === leadKey ? "lg" : "md"}
                      tab={left <= 0 ? "Ends today" : `${left} day${left === 1 ? "" : "s"} left`}
                      heading={`Lease ending — unit ${l.unitNumber}`}
                      meta={`${propertyName.get(l.propertyId) ?? "Property"} · ${tenantName.get(l.tenantId) ?? "tenant"} · ends ${new Date(l.endDate).toLocaleDateString("en-IN", { day: "numeric", month: "short" })}`}
                      clearing={clearing.has(key)}
                      actions={
                        <>
                          <button className="board-btn board-btn--primary" onClick={() => navigate("/leases")}>
                            Review &amp; renew
                          </button>
                          <button
                            className="board-btn"
                            onClick={() =>
                              clear(key, { id: key, label: `Snoozed unit ${l.unitNumber}`, time: nowTime() }, undefined, () =>
                                snooze(key)
                              )
                            }
                          >
                            Snooze a day
                          </button>
                        </>
                      }
                    />
                  );
                })}

                {showWork.map((w) => {
                  const key = `work-${w.id}`;
                  return (
                    <Notice
                      key={key}
                      tone="urgent"
                      size={key === leadKey ? "lg" : "md"}
                      tab="Urgent"
                      heading={w.title}
                      meta={[
                        propertyName.get(w.propertyId) ?? "Property",
                        w.triage?.category ? w.triage.category.replace(/_/g, " ").toLowerCase() : null,
                        costLabel(w.triage?.costBand)
                      ]
                        .filter(Boolean)
                        .join(" · ")}
                      clearing={clearing.has(key)}
                      fine={w.triage?.summary ?? undefined}
                      actions={
                        <>
                          <button
                            className="board-btn board-btn--primary"
                            onClick={() =>
                              clear(
                                key,
                                { id: key, label: `Started "${w.title}"`, time: nowTime() },
                                () => maintenanceApi.update(w.id, { status: "IN_PROGRESS" }),
                                () => {
                                  openWork.reload();
                                  summary.reload();
                                }
                              )
                            }
                          >
                            Start work
                          </button>
                          <button className="board-btn" onClick={() => navigate("/maintenance")}>
                            Open request
                          </button>
                        </>
                      }
                    />
                  );
                })}
              </div>
            </BoardState>
          </BoardSection>

          <Counterfoil items={cleared} />

          {(dueSoon.length > 0 || expiringLater.length > 0 || routineWork.length > 0) && (
            <BoardSection title="Later this month">
              <div className="notice-strip notice-strip--calm">
                {routineWork.map((w) => {
                  const key = `work-routine-${w.id}`;
                  return (
                    <Notice
                      key={key}
                      tone="plain"
                      size="sm"
                      heading={w.title}
                      meta={[
                        propertyName.get(w.propertyId) ?? "Property",
                        w.triage?.category ? w.triage.category.replace(/_/g, " ").toLowerCase() : null,
                        `${priorityOf(w)[0]}${priorityOf(w).slice(1).toLowerCase()} priority`
                      ]
                        .filter(Boolean)
                        .join(" · ")}
                      clearing={clearing.has(key)}
                      actions={
                        <button className="board-btn" onClick={() => navigate("/maintenance")}>
                          Open request
                        </button>
                      }
                    />
                  );
                })}
                {dueSoon.map((p) => {
                  const key = `soon-${p.id}`;
                  const d = daysBetween(p.dueDate);
                  return (
                    <Notice
                      key={key}
                      tone="plain"
                      size="sm"
                      heading={`Rent due — ${tenantName.get(p.tenantId) ?? `tenant #${p.tenantId}`}`}
                      meta={`${propertyName.get(p.propertyId) ?? "Property"} · ${formatCurrency(p.amountDue)} · ${d === 0 ? "due today" : `in ${d} day${d === 1 ? "" : "s"}`}`}
                      clearing={clearing.has(key)}
                      actions={
                        <button className="board-btn" onClick={() => navigate("/payments")}>
                          Open in rent
                        </button>
                      }
                    />
                  );
                })}
                {expiringLater.map((l) => {
                  const key = `lease-later-${l.id}`;
                  return (
                    <Notice
                      key={key}
                      tone="plain"
                      size="sm"
                      heading={`Lease ends ${new Date(l.endDate).toLocaleDateString("en-IN", { day: "numeric", month: "short" })} — unit ${l.unitNumber}`}
                      meta={`${propertyName.get(l.propertyId) ?? "Property"} · ${tenantName.get(l.tenantId) ?? "tenant"}`}
                      clearing={clearing.has(key)}
                      actions={
                        <button className="board-btn" onClick={() => navigate("/leases")}>
                          Review
                        </button>
                      }
                    />
                  );
                })}
              </div>
            </BoardSection>
          )}
        </div>
      </div>

      <BoardSection title="The portfolio" note="Where things stand — nothing here needs action.">
        <BoardState loading={summary.loading} error={summary.error} empty={!s}>
          {s && (
            <Ledger>
              <LedgerRow
                label="Under lease"
                value={`${s.occupiedUnits} / ${s.totalUnits} units`}
                note={`${s.vacantUnits} vacant`}
                slip={
                  hasHistory(occPts) ? (
                    <TrendSlip points={occPts} caption="Units under lease, by month" />
                  ) : undefined
                }
              />
              <LedgerRow
                label="Rent collected"
                value={formatCurrency(s.rentCollected)}
                note={`of ${formatCurrency(s.rentExpected)} billed to date`}
                slip={
                  hasHistory(colPts) ? (
                    <TrendSlip points={colPts} caption="Collected by billing month" format={inCurrency} />
                  ) : undefined
                }
              />
              <LedgerRow
                label="Rent outstanding"
                value={
                  <span className={s.pendingRent > 0 ? "is-arrears" : undefined}>
                    {formatCurrency(s.pendingRent)}
                  </span>
                }
                note={s.pendingRent > 0 ? "across unpaid & overdue charges" : "all charges settled"}
              />
              <LedgerRow
                label="Open maintenance"
                value={`${s.openMaintenanceRequests} request${s.openMaintenanceRequests === 1 ? "" : "s"}`}
                slip={hasHistory(wkPts) ? <TrendSlip points={wkPts} caption="Raised by month" /> : undefined}
              />
              <LedgerRow
                label="Active leases"
                value={String(s.activeLeases)}
                note={
                  s.leasesExpiringThisMonth > 0
                    ? `${s.leasesExpiringThisMonth} end this month`
                    : "none ending this month"
                }
              />
              <LedgerRow label="Active tenants" value={String(s.activeTenants)} />
              <LedgerRow
                label="Properties"
                value={String(s.totalProperties)}
                note={`${s.totalUnits} units in total`}
              />
            </Ledger>
          )}
        </BoardState>
      </BoardSection>
    </Board>
  );
}
