import { useState } from "react";
import type { FormEvent } from "react";
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
import { tenantsApi } from "../lib/resources";
import { useCollection } from "../lib/useCollection";
import { useListView } from "../lib/useListView";
import type { ReliabilityResponse, TenantRequest, TenantResponse, TenantStatus } from "../types";

const STATUSES: TenantStatus[] = ["ACTIVE", "PENDING", "INACTIVE"];

const EMPTY: TenantRequest = {
  fullName: "",
  email: "",
  phone: "",
  emergencyContactName: "",
  emergencyContactPhone: "",
  governmentIdNumber: "",
  status: "PENDING"
};

function toRequest(t: TenantResponse): TenantRequest {
  return {
    fullName: t.fullName,
    email: t.email,
    phone: t.phone,
    emergencyContactName: t.emergencyContactName ?? "",
    emergencyContactPhone: t.emergencyContactPhone ?? "",
    governmentIdNumber: t.governmentIdNumber ?? "",
    status: t.status
  };
}

export function TenantsPage() {
  const list = useListView((params) => tenantsApi.list(params));
  const [editing, setEditing] = useState<TenantResponse | null>(null);
  const [creating, setCreating] = useState(false);
  const [scoreFor, setScoreFor] = useState<TenantResponse | null>(null);

  return (
    <section className="page">
      <PageHeader
        title="Tenants"
        subtitle="Tenant profiles and contact details."
        actions={<Button onClick={() => setCreating(true)}>New tenant</Button>}
      />

      <FilterBar>
        <TextInput
          placeholder="Search name / email / phone"
          defaultValue={list.filters.q ?? ""}
          onChange={(e) => list.setFilter("q", e.target.value)}
        />
        <select
          className="input"
          value={list.filters.status ?? ""}
          onChange={(e) => list.setFilter("status", e.target.value)}
        >
          <option value="">Any status</option>
          {STATUSES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </FilterBar>

      <DataState
        loading={list.loading}
        error={list.error}
        empty={list.rows.length === 0}
        emptyMessage="No tenants match."
      >
        <Table columns={["Name", "Email", "Phone", "Portal", "Status", ""]}>
          {list.rows.map((t) => (
            <tr key={t.id}>
              <td>
                <strong>{t.fullName}</strong>
                {t.governmentIdNumber && <div className="muted">ID: {t.governmentIdNumber}</div>}
              </td>
              <td>{t.email}</td>
              <td>{t.phone}</td>
              <td>{t.userId ? <StatusPill value="ACTIVE" /> : <span className="muted">not linked</span>}</td>
              <td>
                <StatusPill value={t.status} />
              </td>
              <td className="row-actions">
                <Button variant="ghost" onClick={() => setScoreFor(t)}>
                  Reliability
                </Button>
                <Button variant="ghost" onClick={() => setEditing(t)}>
                  Edit
                </Button>
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

      {(creating || editing) && (
        <TenantForm
          initial={editing ? toRequest(editing) : EMPTY}
          title={editing ? `Edit ${editing.fullName}` : "New tenant"}
          onClose={() => {
            setCreating(false);
            setEditing(null);
          }}
          onSubmit={async (body) => {
            if (editing) await tenantsApi.update(editing.id, body);
            else await tenantsApi.create(body);
            setCreating(false);
            setEditing(null);
            list.reload();
          }}
        />
      )}

      {scoreFor && <ReliabilityModal tenant={scoreFor} onClose={() => setScoreFor(null)} />}
    </section>
  );
}

function ReliabilityModal({ tenant, onClose }: { tenant: TenantResponse; onClose: () => void }) {
  const { data, loading, error } = useCollection<ReliabilityResponse>(() => tenantsApi.reliability(tenant.id));
  return (
    <Modal title={`${tenant.fullName} — payment reliability`} onClose={onClose}>
      <DataState loading={loading} error={error} empty={!data}>
        {data && (
          <div className="reliability">
            <div className="reliability-score">
              <strong>{data.score}</strong>
              <StatusPill value={data.band} />
              {data.predictedLateRisk && <span className="pill pill-red">Late-payment risk</span>}
            </div>
            <ul className="reliability-facts">
              <li>On-time payments: {data.onTimeCount}</li>
              <li>Late payments: {data.lateCount}</li>
              <li>Currently overdue: {data.currentlyOverdueCount}</li>
              <li>Average days late: {data.avgDaysLate}</li>
              <li>Outstanding balance: {formatCurrency(data.outstanding)}</li>
              <li>Next charge due: {formatDate(data.nextDueDate)}</li>
            </ul>
          </div>
        )}
      </DataState>
    </Modal>
  );
}

function TenantForm({
  initial,
  title,
  onClose,
  onSubmit
}: {
  initial: TenantRequest;
  title: string;
  onClose: () => void;
  onSubmit: (body: TenantRequest) => Promise<void>;
}) {
  const [form, setForm] = useState<TenantRequest>(initial);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  function set<K extends keyof TenantRequest>(key: K, value: TenantRequest[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await onSubmit(form);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Save failed");
      setBusy(false);
    }
  }

  return (
    <Modal title={title} onClose={onClose}>
      <form className="form-grid" onSubmit={submit}>
        <Field label="Full name">
          <TextInput value={form.fullName} onChange={(e) => set("fullName", e.target.value)} required />
        </Field>
        <div className="form-row">
          <Field label="Email">
            <TextInput
              type="email"
              value={form.email}
              onChange={(e) => set("email", e.target.value)}
              required
            />
          </Field>
          <Field label="Phone">
            <TextInput value={form.phone} onChange={(e) => set("phone", e.target.value)} required />
          </Field>
        </div>
        <div className="form-row">
          <Field label="Emergency contact name">
            <TextInput
              value={form.emergencyContactName ?? ""}
              onChange={(e) => set("emergencyContactName", e.target.value)}
            />
          </Field>
          <Field label="Emergency contact phone">
            <TextInput
              value={form.emergencyContactPhone ?? ""}
              onChange={(e) => set("emergencyContactPhone", e.target.value)}
            />
          </Field>
        </div>
        <div className="form-row">
          <Field label="Government ID">
            <TextInput
              value={form.governmentIdNumber ?? ""}
              onChange={(e) => set("governmentIdNumber", e.target.value)}
            />
          </Field>
          <Field label="Status">
            <Select
              options={STATUSES}
              value={form.status}
              onChange={(e) => set("status", e.target.value as TenantStatus)}
            />
          </Field>
        </div>
        {error && <div className="form-error">{error}</div>}
        <div className="form-actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={busy}>
            {busy ? "Saving…" : "Save"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
