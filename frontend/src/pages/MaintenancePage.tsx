import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Sparkles } from "lucide-react";
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
  TextArea,
  TextInput
} from "../components/ui";
import { ApiError } from "../lib/api";
import { formatDate, titleCase } from "../lib/format";
import { maintenanceApi, propertiesApi, tenantsApi } from "../lib/resources";
import { useCollection } from "../lib/useCollection";
import { useListView } from "../lib/useListView";
import type {
  MaintenanceCreateRequest,
  MaintenancePriority,
  MaintenanceResponse,
  MaintenanceStatus,
  PropertyResponse,
  TenantResponse
} from "../types";

const PRIORITIES: MaintenancePriority[] = ["LOW", "MEDIUM", "HIGH", "URGENT"];
const STATUSES: MaintenanceStatus[] = ["OPEN", "IN_PROGRESS", "RESOLVED", "CLOSED"];

export function MaintenancePage() {
  const { isManager } = useAuth();
  const list = useListView((params) => maintenanceApi.list(params));
  const properties = useCollection(() => propertiesApi.list({ size: 200 }));
  const tenants = useCollection(() => tenantsApi.list({ size: 200 }));
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<MaintenanceResponse | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

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

  async function act(id: number, fn: () => Promise<unknown>) {
    setBusyId(id);
    try {
      await fn();
      await list.reload();
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section className="page">
      <PageHeader
        title="Maintenance"
        subtitle={
          isManager
            ? "Requests are auto-triaged on arrival — category, priority, cost band and a draft reply."
            : "Raise a request and follow its progress."
        }
        actions={<Button onClick={() => setCreating(true)}>Log request</Button>}
      />

      <FilterBar>
        <TextInput
          placeholder="Search title / description"
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
              {titleCase(s)}
            </option>
          ))}
        </select>
        <select
          className="input"
          value={list.filters.priority ?? ""}
          onChange={(e) => list.setFilter("priority", e.target.value)}
        >
          <option value="">Any priority</option>
          {PRIORITIES.map((p) => (
            <option key={p} value={p}>
              {titleCase(p)}
            </option>
          ))}
        </select>
      </FilterBar>

      <DataState
        loading={list.loading}
        error={list.error}
        empty={list.rows.length === 0}
        emptyMessage="No maintenance requests match."
      >
        <Table
          columns={
            isManager
              ? ["Issue", "Property", "Reported by", "Priority", "Status", ""]
              : ["Issue", "Property", "Priority", "Status"]
          }
        >
          {list.rows.map((r) => (
            <tr key={r.id}>
              <td>
                <strong>{r.title}</strong>
                <div className="muted">{r.description}</div>
                {r.managerNotes && <div className="muted">Note: {r.managerNotes}</div>}
                {r.resolvedAt && <div className="muted">Resolved {formatDate(r.resolvedAt)}</div>}
                {isManager && r.triage?.triaged && (
                  <TriageCard
                    request={r}
                    busy={busyId === r.id}
                    onApply={() => act(r.id, () => maintenanceApi.acceptSuggestion(r.id))}
                    onRetriage={() => act(r.id, () => maintenanceApi.retriage(r.id))}
                  />
                )}
              </td>
              <td>{propertyName.get(r.propertyId) ?? `#${r.propertyId}`}</td>
              {isManager && <td>{tenantName.get(r.tenantId) ?? `#${r.tenantId}`}</td>}
              <td>
                <StatusPill value={r.priority} />
              </td>
              <td>
                <StatusPill value={r.status} />
              </td>
              {isManager && (
                <td className="row-actions">
                  <Button variant="ghost" onClick={() => setEditing(r)}>
                    Update
                  </Button>
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
        <CreateForm
          properties={properties.data?.content ?? []}
          tenants={tenants.data?.content ?? []}
          hideReporter={!isManager}
          onClose={() => setCreating(false)}
          onSubmit={async (body) => {
            await maintenanceApi.create(body);
            setCreating(false);
            list.reload();
          }}
        />
      )}

      {editing && (
        <UpdateForm
          request={editing}
          onClose={() => setEditing(null)}
          onSubmit={async (status, managerNotes) => {
            await maintenanceApi.update(editing.id, { status, managerNotes });
            setEditing(null);
            list.reload();
          }}
        />
      )}
    </section>
  );
}

function TriageCard({
  request,
  busy,
  onApply,
  onRetriage
}: {
  request: MaintenanceResponse;
  busy: boolean;
  onApply: () => void;
  onRetriage: () => void;
}) {
  const t = request.triage;
  const priorityDiffers = t.suggestedPriority && t.suggestedPriority !== request.priority;
  return (
    <div className="triage-card">
      <div className="triage-head">
        <Sparkles size={14} />
        <span>
          AI triage · {t.category} · suggests <strong>{t.suggestedPriority}</strong> · {t.costBand}
        </span>
        <span className="triage-source">{t.source === "CLAUDE" ? "Claude" : "rules"}</span>
      </div>
      {t.summary && <div className="muted">{t.summary}</div>}
      {t.draftReply && (
        <details className="triage-reply">
          <summary>Draft reply to tenant</summary>
          <p>{t.draftReply}</p>
        </details>
      )}
      <div className="row-actions">
        {priorityDiffers && (
          <Button variant="secondary" disabled={busy} onClick={onApply}>
            Apply {t.suggestedPriority} priority
          </Button>
        )}
        <Button variant="ghost" disabled={busy} onClick={onRetriage}>
          Re-triage
        </Button>
      </div>
    </div>
  );
}

function CreateForm({
  properties,
  tenants,
  hideReporter,
  onClose,
  onSubmit
}: {
  properties: PropertyResponse[];
  tenants: TenantResponse[];
  hideReporter?: boolean;
  onClose: () => void;
  onSubmit: (body: MaintenanceCreateRequest) => Promise<void>;
}) {
  const [form, setForm] = useState<MaintenanceCreateRequest>({
    tenantId: tenants[0]?.id ?? 0,
    propertyId: properties[0]?.id ?? 0,
    title: "",
    description: "",
    priority: "MEDIUM"
  });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  function set<K extends keyof MaintenanceCreateRequest>(key: K, value: MaintenanceCreateRequest[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await onSubmit({ ...form, tenantId: Number(form.tenantId), propertyId: Number(form.propertyId) });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Save failed");
      setBusy(false);
    }
  }

  const canSubmit = properties.length > 0 && (hideReporter || tenants.length > 0);

  return (
    <Modal title="Log maintenance request" onClose={onClose}>
      <form className="form-grid" onSubmit={submit}>
        {!canSubmit && (
          <div className="form-error">
            {hideReporter
              ? "You have no active lease to raise a request against."
              : "Add a property and a tenant first."}
          </div>
        )}
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
          {!hideReporter && (
            <Field label="Reported by">
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
          )}
        </div>
        <Field label="Title">
          <TextInput value={form.title} onChange={(e) => set("title", e.target.value)} required />
        </Field>
        <Field label="Description">
          <TextArea
            value={form.description}
            onChange={(e) => set("description", e.target.value)}
            required
          />
        </Field>
        <Field label="Priority">
          <Select
            options={PRIORITIES}
            value={form.priority}
            onChange={(e) => set("priority", e.target.value as MaintenancePriority)}
          />
        </Field>
        {error && <div className="form-error">{error}</div>}
        <div className="form-actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={busy || !canSubmit}>
            {busy ? "Saving…" : "Submit"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function UpdateForm({
  request,
  onClose,
  onSubmit
}: {
  request: MaintenanceResponse;
  onClose: () => void;
  onSubmit: (status: MaintenanceStatus, managerNotes: string) => Promise<void>;
}) {
  const [status, setStatus] = useState<MaintenanceStatus>(request.status);
  const [notes, setNotes] = useState(request.managerNotes ?? request.triage?.draftReply ?? "");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await onSubmit(status, notes);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Save failed");
      setBusy(false);
    }
  }

  return (
    <Modal title={request.title} onClose={onClose}>
      <form className="form-grid" onSubmit={submit}>
        <Field label="Status">
          <Select
            options={STATUSES}
            value={status}
            onChange={(e) => setStatus(e.target.value as MaintenanceStatus)}
          />
        </Field>
        <Field label="Manager notes">
          <TextArea value={notes} onChange={(e) => setNotes(e.target.value)} />
        </Field>
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
