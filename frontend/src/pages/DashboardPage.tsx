import { AlarmClock, Building2, DoorOpen, FileText, IndianRupee, Layers, ShieldAlert, TrendingUp, Users, Wrench } from "lucide-react";
import { useAuth } from "../auth/AuthContext";
import { PageHeader, DataState, StatusPill, Table } from "../components/ui";
import { formatCurrency, formatDate } from "../lib/format";
import { dashboardApi } from "../lib/resources";
import { useCollection } from "../lib/useCollection";

export function DashboardPage() {
  const { user } = useAuth();
  const summary = useCollection(() => dashboardApi.summary());
  const risk = useCollection(() => dashboardApi.rentAtRisk());
  const data = summary.data;

  const collectionRate =
    data && data.rentExpected > 0 ? Math.round((data.rentCollected / data.rentExpected) * 100) : 0;
  const occupancyRate =
    data && data.totalUnits > 0 ? Math.round((data.occupiedUnits / data.totalUnits) * 100) : 0;

  const cards = data
    ? [
        { label: "Properties", value: String(data.totalProperties), icon: Building2 },
        { label: "Units (occupied/total)", value: `${data.occupiedUnits}/${data.totalUnits}`, icon: Layers },
        { label: "Vacant units", value: String(data.vacantUnits), icon: DoorOpen },
        { label: "Occupancy rate", value: `${occupancyRate}%`, icon: TrendingUp },
        { label: "Active tenants", value: String(data.activeTenants), icon: Users },
        { label: "Active leases", value: String(data.activeLeases), icon: FileText },
        { label: "Expiring this month", value: String(data.leasesExpiringThisMonth), icon: AlarmClock },
        { label: "Open maintenance", value: String(data.openMaintenanceRequests), icon: Wrench },
        { label: "Rent expected", value: formatCurrency(data.rentExpected), icon: IndianRupee },
        { label: "Rent collected", value: formatCurrency(data.rentCollected), icon: IndianRupee },
        { label: "Rent pending", value: formatCurrency(data.pendingRent), icon: IndianRupee },
        { label: "Collection rate", value: `${collectionRate}%`, icon: TrendingUp }
      ]
    : [];

  return (
    <section className="page">
      <PageHeader
        title={`Welcome back, ${user?.fullName?.split(" ")[0] ?? ""}`}
        subtitle="A snapshot of your rental portfolio."
      />
      <DataState loading={summary.loading} error={summary.error} empty={!data}>
        <div className="metric-grid">
          {cards.map((c) => {
            const Icon = c.icon;
            return (
              <article className="metric-card" key={c.label}>
                <Icon size={20} />
                <span>{c.label}</span>
                <strong>{c.value}</strong>
              </article>
            );
          })}
        </div>
      </DataState>

      <div>
        <h3 className="section-h">
          <ShieldAlert size={16} /> Rent at risk
        </h3>
        <DataState
          loading={risk.loading}
          error={risk.error}
          empty={!risk.data || risk.data.tenants.length === 0}
          emptyMessage="No tenants are flagged as a late-payment risk right now."
        >
          {risk.data && (
            <>
              <p className="muted">
                {risk.data.tenantsAtRisk} tenant{risk.data.tenantsAtRisk === 1 ? "" : "s"} likely to pay
                late · {formatCurrency(risk.data.exposureAmount)} outstanding exposure
              </p>
              <Table columns={["Tenant", "Reliability", "On-time / late", "Overdue", "Outstanding", "Next due"]}>
                {risk.data.tenants.map((t) => (
                  <tr key={t.tenantId}>
                    <td>
                      <strong>{t.tenantName}</strong>
                    </td>
                    <td>
                      <StatusPill value={t.band} /> <span className="muted">{t.score}</span>
                    </td>
                    <td>
                      {t.onTimeCount} / {t.lateCount}
                      {t.avgDaysLate > 0 && <span className="muted"> · avg {t.avgDaysLate}d late</span>}
                    </td>
                    <td>{t.currentlyOverdueCount}</td>
                    <td>{formatCurrency(t.outstanding)}</td>
                    <td>{formatDate(t.nextDueDate)}</td>
                  </tr>
                ))}
              </Table>
            </>
          )}
        </DataState>
      </div>
    </section>
  );
}
