import { useMemo } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { PageHeader, DataState, StatusPill, Table } from "../components/ui";
import { ApiError } from "../lib/api";
import { formatCurrency, formatDate } from "../lib/format";
import { leasesApi, paymentsApi, propertiesApi, tenantsApi } from "../lib/resources";
import { useCollection } from "../lib/useCollection";

export function TenantHomePage() {
  const { user } = useAuth();
  const profile = useCollection(() => tenantsApi.me());
  const leases = useCollection(() => leasesApi.list());
  const payments = useCollection(() => paymentsApi.list());
  const properties = useCollection(() => propertiesApi.list());

  const propertyName = useMemo(() => {
    const m = new Map<number, string>();
    properties.data?.content.forEach((p) => m.set(p.id, p.name));
    return m;
  }, [properties.data]);

  const notLinked = !profile.loading && !!profile.error;

  const leaseRows = leases.data?.content ?? [];
  const activeLease = leaseRows.find((l) => l.leaseStatus === "ACTIVE") ?? leaseRows[0];

  const paymentRows = payments.data?.content ?? [];
  const outstanding = paymentRows
    .filter((p) => p.paymentStatus !== "PAID")
    .reduce((sum, p) => sum + (p.amountDue - p.amountPaid), 0);
  const nextDue = paymentRows
    .filter((p) => p.paymentStatus !== "PAID")
    .sort((a, b) => a.dueDate.localeCompare(b.dueDate))[0];

  return (
    <section className="page">
      <PageHeader
        title={`Hello, ${user?.fullName?.split(" ")[0] ?? ""}`}
        subtitle="Your lease, rent, and maintenance in one place."
      />

      {notLinked ? (
        <div className="data-state">
          Your account isn't linked to a tenant profile yet. Ask your property manager to add
          you using <strong>{user?.email}</strong>.
        </div>
      ) : (
        <>
          <div className="metric-grid">
            <article className="metric-card">
              <span>Home</span>
              <strong>{activeLease ? propertyName.get(activeLease.propertyId) ?? "—" : "—"}</strong>
            </article>
            <article className="metric-card">
              <span>Monthly rent</span>
              <strong>{activeLease ? formatCurrency(activeLease.monthlyRent) : "—"}</strong>
            </article>
            <article className="metric-card">
              <span>Outstanding</span>
              <strong>{formatCurrency(outstanding)}</strong>
            </article>
            <article className="metric-card">
              <span>Next due</span>
              <strong>{nextDue ? formatDate(nextDue.dueDate) : "—"}</strong>
            </article>
          </div>

          <div>
            <h3 className="section-h">Lease</h3>
            <DataState
              loading={leases.loading}
              error={leases.error}
              empty={leaseRows.length === 0}
              emptyMessage="No lease on file yet."
            >
              <Table columns={["Property", "Unit", "Term", "Rent", "Deposit", "Status"]}>
                {leaseRows.map((l) => (
                  <tr key={l.id}>
                    <td>{propertyName.get(l.propertyId) ?? `#${l.propertyId}`}</td>
                    <td>{l.unitNumber}</td>
                    <td>
                      {formatDate(l.startDate)} → {formatDate(l.endDate)}
                    </td>
                    <td>{formatCurrency(l.monthlyRent)}</td>
                    <td>{formatCurrency(l.securityDeposit)}</td>
                    <td>
                      <StatusPill value={l.leaseStatus} />
                    </td>
                  </tr>
                ))}
              </Table>
            </DataState>
          </div>

          <div>
            <h3 className="section-h">Recent rent</h3>
            <DataState
              loading={payments.loading}
              error={payments.error}
              empty={paymentRows.length === 0}
              emptyMessage="No rent charges yet."
            >
              <Table columns={["Due", "Amount", "Paid", "Status"]}>
                {paymentRows.slice(0, 6).map((p) => (
                  <tr key={p.id}>
                    <td>{formatDate(p.dueDate)}</td>
                    <td>{formatCurrency(p.amountDue)}</td>
                    <td>
                      {formatCurrency(p.amountPaid)}
                      {p.paidDate && <div className="muted">{formatDate(p.paidDate)}</div>}
                    </td>
                    <td>
                      <StatusPill value={p.paymentStatus} />
                    </td>
                  </tr>
                ))}
              </Table>
            </DataState>
          </div>

          <p className="muted">
            Something broken at home? <Link to="/maintenance">Raise a maintenance request</Link>.
          </p>
        </>
      )}
    </section>
  );
}
