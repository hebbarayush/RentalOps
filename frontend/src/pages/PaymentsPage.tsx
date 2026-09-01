import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { useAuth } from "../auth/AuthContext";
import {
  PageHeader,
  Button,
  DataState,
  Field,
  FilterBar,
  Modal,
  Pagination,
  Select,
  StatusPill,
  Table,
  TextInput
} from "../components/ui";
import { ApiError } from "../lib/api";
import { formatCurrency, formatDate } from "../lib/format";
import { leasesApi, paymentsApi, propertiesApi, tenantsApi } from "../lib/resources";
import { useCollection } from "../lib/useCollection";
import { useListView } from "../lib/useListView";
import type { LeaseResponse, MarkPaymentRequest, PaymentMethod, RentPaymentRequest, RentPaymentResponse } from "../types";

const METHODS: PaymentMethod[] = ["UPI", "BANK_TRANSFER", "CARD", "CASH", "OTHER"];

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export function PaymentsPage() {
  const { isManager, hasRole } = useAuth();
  const isAdmin = hasRole("ADMIN");
  const list = useListView((params) => paymentsApi.list(params));
  const leases = useCollection(() => leasesApi.list({ size: 200 }));
  const properties = useCollection(() => propertiesApi.list({ size: 200 }));
  const tenants = useCollection(() => tenantsApi.list({ size: 200 }));
  const [creating, setCreating] = useState(false);
  const [marking, setMarking] = useState<RentPaymentResponse | null>(null);
  const [msg, setMsg] = useState("");

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

  return (
    <section className="page">
      <PageHeader
        title={isManager ? "Rent payments" : "My rent"}
        subtitle={
          isManager
            ? "Charges are generated automatically from active leases; record collections here."
            : "Your rent charges and payment history."
        }
        actions={
          isManager ? (
            <div className="row-actions">
              {isAdmin && (
                <Button
                  variant="secondary"
                  onClick={async () => {
                    const r = await paymentsApi.runBilling();
                    setMsg(`Billing run created ${r.created} charge(s).`);
                    list.reload();
                  }}
                >
                  Run billing
                </Button>
              )}
              <Button onClick={() => setCreating(true)}>Record charge</Button>
            </div>
          ) : undefined
        }
      />

      <FilterBar>
        <select
          className="input"
          value={list.filters.status ?? ""}
          onChange={(e) => list.setFilter("status", e.target.value)}
        >
          <option value="">Any status</option>
          {["PENDING", "OVERDUE", "PARTIAL", "PAID", "FAILED"].map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={list.filters.unpaidOnly === "true"}
            onChange={(e) => list.setFilter("unpaidOnly", e.target.checked ? "true" : "")}
          />
          Unpaid only
        </label>
      </FilterBar>

      {msg && <div className="form-note">{msg}</div>}

      <DataState
        loading={list.loading}
        error={list.error}
        empty={list.rows.length === 0}
        emptyMessage="No charges match."
      >
        <Table
          columns={
            isManager
              ? ["Property", "Tenant", "Due", "Amount due", "Paid", "Status", ""]
              : ["Property", "Due", "Amount due", "Paid", "Status"]
          }
        >
          {list.rows.map((p) => (
            <tr key={p.id}>
              <td>{propertyName.get(p.propertyId) ?? `#${p.propertyId}`}</td>
              {isManager && <td>{tenantName.get(p.tenantId) ?? `#${p.tenantId}`}</td>}
              <td>{formatDate(p.dueDate)}</td>
              <td>{formatCurrency(p.amountDue)}</td>
              <td>
                {formatCurrency(p.amountPaid)}
                {p.paidDate && <div className="muted">{formatDate(p.paidDate)}</div>}
              </td>
              <td>
                <StatusPill value={p.paymentStatus} />
              </td>
              {isManager && (
                <td className="row-actions">
                  {p.paymentStatus !== "PAID" && (
                    <Button variant="secondary" onClick={() => setMarking(p)}>
                      Mark paid
                    </Button>
                  )}
                </td>
              )}
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
        <ChargeForm
          leases={leases.data?.content ?? []}
          onClose={() => setCreating(false)}
          onSubmit={async (body) => {
            await paymentsApi.create(body);
            setCreating(false);
            list.reload();
          }}
        />
      )}

      {marking && (
        <MarkPaidForm
          payment={marking}
          onClose={() => setMarking(null)}
          onSubmit={async (body) => {
            await paymentsApi.markPaid(marking.id, body);
            setMarking(null);
            list.reload();
          }}
        />
      )}
    </section>
  );
}

function ChargeForm({
  leases,
  onClose,
  onSubmit
}: {
  leases: LeaseResponse[];
  onClose: () => void;
  onSubmit: (body: RentPaymentRequest) => Promise<void>;
}) {
  const activeLeases = leases.filter((l) => l.leaseStatus === "ACTIVE");
  const [form, setForm] = useState<RentPaymentRequest>({
    leaseId: activeLeases[0]?.id ?? 0,
    amountDue: activeLeases[0]?.monthlyRent ?? 0,
    dueDate: today(),
    notes: ""
  });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  function set<K extends keyof RentPaymentRequest>(key: K, value: RentPaymentRequest[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await onSubmit({ ...form, leaseId: Number(form.leaseId), amountDue: Number(form.amountDue) });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Save failed");
      setBusy(false);
    }
  }

  return (
    <Modal title="Record rent charge" onClose={onClose}>
      <form className="form-grid" onSubmit={submit}>
        {activeLeases.length === 0 && (
          <div className="form-error">No active leases. Activate a lease first.</div>
        )}
        <Field label="Lease">
          <select
            className="input"
            value={form.leaseId}
            onChange={(e) => {
              const id = Number(e.target.value);
              const lease = activeLeases.find((l) => l.id === id);
              setForm((f) => ({ ...f, leaseId: id, amountDue: lease?.monthlyRent ?? f.amountDue }));
            }}
          >
            {activeLeases.map((l) => (
              <option key={l.id} value={l.id}>
                Lease #{l.id} · unit {l.unitNumber} · {formatCurrency(l.monthlyRent)}/mo
              </option>
            ))}
          </select>
        </Field>
        <div className="form-row">
          <Field label="Amount due">
            <TextInput
              type="number"
              min={1}
              value={form.amountDue}
              onChange={(e) => set("amountDue", Number(e.target.value))}
              required
            />
          </Field>
          <Field label="Due date">
            <TextInput
              type="date"
              value={form.dueDate}
              onChange={(e) => set("dueDate", e.target.value)}
              required
            />
          </Field>
        </div>
        <Field label="Notes">
          <TextInput value={form.notes ?? ""} onChange={(e) => set("notes", e.target.value)} />
        </Field>
        {error && <div className="form-error">{error}</div>}
        <div className="form-actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={busy || activeLeases.length === 0}>
            {busy ? "Saving…" : "Save"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function MarkPaidForm({
  payment,
  onClose,
  onSubmit
}: {
  payment: RentPaymentResponse;
  onClose: () => void;
  onSubmit: (body: MarkPaymentRequest) => Promise<void>;
}) {
  const [form, setForm] = useState<MarkPaymentRequest>({
    amountPaid: payment.amountDue,
    paymentMethod: "UPI",
    transactionReference: ""
  });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await onSubmit({ ...form, amountPaid: Number(form.amountPaid) });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Save failed");
      setBusy(false);
    }
  }

  return (
    <Modal title={`Mark payment #${payment.id} paid`} onClose={onClose}>
      <form className="form-grid" onSubmit={submit}>
        <div className="form-row">
          <Field label="Amount paid">
            <TextInput
              type="number"
              min={1}
              value={form.amountPaid}
              onChange={(e) => setForm((f) => ({ ...f, amountPaid: Number(e.target.value) }))}
              required
            />
          </Field>
          <Field label="Method">
            <Select
              options={METHODS}
              value={form.paymentMethod}
              onChange={(e) =>
                setForm((f) => ({ ...f, paymentMethod: e.target.value as PaymentMethod }))
              }
            />
          </Field>
        </div>
        <Field label="Transaction reference">
          <TextInput
            value={form.transactionReference ?? ""}
            onChange={(e) => setForm((f) => ({ ...f, transactionReference: e.target.value }))}
          />
        </Field>
        {error && <div className="form-error">{error}</div>}
        <div className="form-actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={busy}>
            {busy ? "Saving…" : "Confirm payment"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
