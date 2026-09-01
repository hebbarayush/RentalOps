import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import {
  PageHeader,
  Button,
  DataState,
  Field,
  FilterBar,
  Modal,
  Pagination,
  StatusPill,
  Table,
  TextInput
} from "../components/ui";
import { ApiError } from "../lib/api";
import { formatCurrency, formatDate } from "../lib/format";
import { leasesApi, propertiesApi, tenantsApi } from "../lib/resources";
import { useCollection } from "../lib/useCollection";
import { useListView } from "../lib/useListView";
import type { LeaseRenewalRequest, LeaseRequest, LeaseResponse, PropertyResponse, TenantResponse } from "../types";

function todayPlus(months: number): string {
  const d = new Date();
  d.setMonth(d.getMonth() + months);
  return d.toISOString().slice(0, 10);
}

export function LeasesPage() {
  const list = useListView((params) => leasesApi.list(params));
  const properties = useCollection(() => propertiesApi.list({ size: 200 }));
  const tenants = useCollection(() => tenantsApi.list({ size: 200 }));
  const [creating, setCreating] = useState(false);
  const [renewing, setRenewing] = useState<LeaseResponse | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [actionError, setActionError] = useState("");
  const [actionMsg, setActionMsg] = useState("");

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

  async function runAction(id: number, fn: () => Promise<unknown>, done?: (r: unknown) => void) {
    setBusyId(id);
    setActionError("");
    setActionMsg("");
    try {
      const r = await fn();
      done?.(r);
      await list.reload();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Action failed");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section className="page">
      <PageHeader
        title="Leases"
        subtitle="Draft, activate, renew, and bill lease agreements."
        actions={<Button onClick={() => setCreating(true)}>New lease</Button>}
      />

      <FilterBar>
        <TextInput
          placeholder="Search unit number"
          defaultValue={list.filters.q ?? ""}
          onChange={(e) => list.setFilter("q", e.target.value)}
        />
        <select
          className="input"
          value={list.filters.status ?? ""}
          onChange={(e) => list.setFilter("status", e.target.value)}
        >
          <option value="">Any status</option>
          {["DRAFT", "ACTIVE", "EXPIRED", "TERMINATED"].map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </FilterBar>

      {actionError && <div className="form-error">{actionError}</div>}
      {actionMsg && <div className="form-note">{actionMsg}</div>}

      <DataState
        loading={list.loading}
        error={list.error}
        empty={list.rows.length === 0}
        emptyMessage="No leases match."
      >
        <Table columns={["Property / unit", "Tenant", "Term", "Rent", "Next charge", "Status", ""]}>
          {list.rows.map((l) => (
            <tr key={l.id}>
              <td>
                <strong>{propertyName.get(l.propertyId) ?? `Property #${l.propertyId}`}</strong>
                <div className="muted">Unit {l.unitNumber}</div>
              </td>
              <td>{tenantName.get(l.tenantId) ?? `Tenant #${l.tenantId}`}</td>
              <td>
                {formatDate(l.startDate)} → {formatDate(l.endDate)}
              </td>
              <td>{formatCurrency(l.monthlyRent)}</td>
              <td>{l.leaseStatus === "ACTIVE" ? formatDate(l.nextChargeDate) : "—"}</td>
              <td>
                <StatusPill value={l.leaseStatus} />
                {l.renewedFromLeaseId && <div className="muted">renewed from #{l.renewedFromLeaseId}</div>}
              </td>
              <td className="row-actions">
                {l.leaseStatus === "DRAFT" && (
                  <Button
                    variant="secondary"
                    disabled={busyId === l.id}
                    onClick={() => runAction(l.id, () => leasesApi.activate(l.id))}
                  >
                    Activate
                  </Button>
                )}
                {l.leaseStatus === "ACTIVE" && (
                  <>
                    <Button
                      variant="ghost"
                      disabled={busyId === l.id}
                      onClick={() =>
                        runAction(
                          l.id,
                          () => leasesApi.generateCharges(l.id),
                          (r) =>
                            setActionMsg(`Generated ${(r as { created: number }).created} rent charge(s).`)
                        )
                      }
                    >
                      Bill now
                    </Button>
                    <Button variant="ghost" onClick={() => setRenewing(l)}>
                      Renew
                    </Button>
                    <Button
                      variant="danger"
                      disabled={busyId === l.id}
                      onClick={() => runAction(l.id, () => leasesApi.terminate(l.id))}
                    >
                      Terminate
                    </Button>
                  </>
                )}
              </td>
            </tr>
          ))}
        </Table>
      </DataState>

      <Pagination
        page={list.page}
        totalPages={list.totalPages}
        totalElements={list.totalElements}
        onPage={list.setPage}
      />

      {creating && (
        <LeaseForm
          properties={properties.data?.content ?? []}
          tenants={tenants.data?.content ?? []}
          onClose={() => setCreating(false)}
          onSubmit={async (body) => {
            await leasesApi.create(body);
            setCreating(false);
            list.reload();
          }}
        />
      )}

      {renewing && (
        <RenewForm
          lease={renewing}
          onClose={() => setRenewing(null)}
          onSubmit={async (body) => {
            await leasesApi.renew(renewing.id, body);
            setRenewing(null);
            list.reload();
          }}
        />
      )}
    </section>
  );
}

function RenewForm({
  lease,
  onClose,
  onSubmit
}: {
  lease: LeaseResponse;
  onClose: () => void;
  onSubmit: (body: LeaseRenewalRequest) => Promise<void>;
}) {
  const [endDate, setEndDate] = useState(todayPlus(24));
  const [monthlyRent, setMonthlyRent] = useState(lease.monthlyRent);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await onSubmit({ endDate, monthlyRent: Number(monthlyRent) });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Save failed");
      setBusy(false);
    }
  }

  return (
    <Modal title={`Renew lease — unit ${lease.unitNumber}`} onClose={onClose}>
      <form className="form-grid" onSubmit={submit}>
        <p className="muted">
          Creates a new DRAFT lease starting the day after {formatDate(lease.endDate)}, same unit and tenant.
        </p>
        <div className="form-row">
          <Field label="New end date">
            <TextInput type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} required />
          </Field>
          <Field label="Monthly rent">
            <TextInput
              type="number"
              min={1}
              value={monthlyRent}
              onChange={(e) => setMonthlyRent(Number(e.target.value))}
              required
            />
          </Field>
        </div>
        {error && <div className="form-error">{error}</div>}
        <div className="form-actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={busy}>
            {busy ? "Saving…" : "Create renewal"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function LeaseForm({
  properties,
  tenants,
  onClose,
  onSubmit
}: {
  properties: PropertyResponse[];
  tenants: TenantResponse[];
  onClose: () => void;
  onSubmit: (body: LeaseRequest) => Promise<void>;
}) {
  const [form, setForm] = useState<LeaseRequest>({
    propertyId: properties[0]?.id ?? 0,
    tenantId: tenants[0]?.id ?? 0,
    unitNumber: "",
    startDate: todayPlus(0),
    endDate: todayPlus(12),
    monthlyRent: 0,
    securityDeposit: 0
  });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  function set<K extends keyof LeaseRequest>(key: K, value: LeaseRequest[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await onSubmit({
        ...form,
        propertyId: Number(form.propertyId),
        tenantId: Number(form.tenantId),
        monthlyRent: Number(form.monthlyRent),
        securityDeposit: Number(form.securityDeposit)
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Save failed");
      setBusy(false);
    }
  }

  const canSubmit = properties.length > 0 && tenants.length > 0;

  return (
    <Modal title="New lease" onClose={onClose}>
      <form className="form-grid" onSubmit={submit}>
        {!canSubmit && <div className="form-error">Add at least one property and one tenant first.</div>}
        <div className="form-row">
          <Field label="Property">
            <select
              className="input"
              value={form.propertyId}
              onChange={(e) => set("propertyId", Number(e.target.value))}
            >
              {properties.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </Field>
          <Field label="Tenant">
            <select
              className="input"
              value={form.tenantId}
              onChange={(e) => set("tenantId", Number(e.target.value))}
            >
              {tenants.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.fullName}
                </option>
              ))}
            </select>
          </Field>
        </div>
        <Field label="Unit number">
          <TextInput value={form.unitNumber} onChange={(e) => set("unitNumber", e.target.value)} required />
        </Field>
        <div className="form-row">
          <Field label="Start date">
            <TextInput
              type="date"
              value={form.startDate}
              onChange={(e) => set("startDate", e.target.value)}
              required
            />
          </Field>
          <Field label="End date">
            <TextInput
              type="date"
              value={form.endDate}
              onChange={(e) => set("endDate", e.target.value)}
              required
            />
          </Field>
        </div>
        <div className="form-row">
          <Field label="Monthly rent">
            <TextInput
              type="number"
              min={1}
              value={form.monthlyRent}
              onChange={(e) => set("monthlyRent", Number(e.target.value))}
              required
            />
          </Field>
          <Field label="Security deposit">
            <TextInput
              type="number"
              min={0}
              value={form.securityDeposit}
              onChange={(e) => set("securityDeposit", Number(e.target.value))}
              required
            />
          </Field>
        </div>
        {error && <div className="form-error">{error}</div>}
        <div className="form-actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={busy || !canSubmit}>
            {busy ? "Saving…" : "Create lease"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
