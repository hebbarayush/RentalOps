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
  TextArea,
  TextInput
} from "../components/ui";
import { ApiError } from "../lib/api";
import { titleCase } from "../lib/format";
import { propertiesApi } from "../lib/resources";
import { useListView } from "../lib/useListView";
import type { PropertyRequest, PropertyResponse, PropertyType } from "../types";

const TYPES: PropertyType[] = ["APARTMENT", "HOUSE", "COMMERCIAL", "VILLA", "STUDIO"];

const EMPTY: PropertyRequest = {
  name: "",
  description: "",
  addressLine1: "",
  addressLine2: "",
  city: "",
  state: "",
  postalCode: "",
  country: "India",
  propertyType: "APARTMENT",
  totalUnits: 1
};

function toRequest(p: PropertyResponse): PropertyRequest {
  return {
    name: p.name,
    description: p.description ?? "",
    addressLine1: p.addressLine1,
    addressLine2: p.addressLine2 ?? "",
    city: p.city,
    state: p.state,
    postalCode: p.postalCode,
    country: p.country,
    propertyType: p.propertyType,
    totalUnits: p.totalUnits
  };
}

export function PropertiesPage() {
  const list = useListView((params) => propertiesApi.list(params));
  const [editing, setEditing] = useState<PropertyResponse | null>(null);
  const [creating, setCreating] = useState(false);
  const [actionError, setActionError] = useState("");

  async function deactivate(p: PropertyResponse) {
    if (!confirm(`Deactivate ${p.name}?`)) return;
    setActionError("");
    try {
      await propertiesApi.deactivate(p.id);
      list.reload();
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Could not deactivate this property");
    }
  }

  return (
    <section className="page">
      <PageHeader
        title="Properties"
        subtitle="Add, update, and review the properties you manage."
        actions={<Button onClick={() => setCreating(true)}>New property</Button>}
      />

      <FilterBar>
        <TextInput
          placeholder="Search name / address / city"
          defaultValue={list.filters.q ?? ""}
          onChange={(e) => list.setFilter("q", e.target.value)}
        />
        <select className="input" value={list.filters.type ?? ""} onChange={(e) => list.setFilter("type", e.target.value)}>
          <option value="">All types</option>
          {TYPES.map((t) => (
            <option key={t} value={t}>
              {titleCase(t)}
            </option>
          ))}
        </select>
        <select
          className="input"
          value={list.filters.status ?? ""}
          onChange={(e) => list.setFilter("status", e.target.value)}
        >
          <option value="">Any status</option>
          <option value="ACTIVE">Active</option>
          <option value="INACTIVE">Inactive</option>
        </select>
      </FilterBar>

      {actionError && <div className="form-error">{actionError}</div>}

      <DataState
        loading={list.loading}
        error={list.error}
        empty={list.rows.length === 0}
        emptyMessage="No properties match."
      >
        <Table columns={["Name", "Location", "Type", "Units", "Status", ""]}>
          {list.rows.map((p) => (
            <tr key={p.id}>
              <td>
                <strong>{p.name}</strong>
                {p.description && <div className="muted">{p.description}</div>}
              </td>
              <td>
                {p.city}, {p.state}
              </td>
              <td>{titleCase(p.propertyType)}</td>
              <td>
                {p.occupiedUnits}/{p.totalUnits}
              </td>
              <td>
                <StatusPill value={p.status} />
              </td>
              <td className="row-actions">
                <Button variant="ghost" onClick={() => setEditing(p)}>
                  Edit
                </Button>
                {p.status === "ACTIVE" && (
                  <Button variant="danger" onClick={() => deactivate(p)}>
                    Deactivate
                  </Button>
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

      {(creating || editing) && (
        <PropertyForm
          initial={editing ? toRequest(editing) : EMPTY}
          title={editing ? `Edit ${editing.name}` : "New property"}
          onClose={() => {
            setCreating(false);
            setEditing(null);
          }}
          onSubmit={async (body) => {
            if (editing) await propertiesApi.update(editing.id, body);
            else await propertiesApi.create(body);
            setCreating(false);
            setEditing(null);
            list.reload();
          }}
        />
      )}
    </section>
  );
}

function PropertyForm({
  initial,
  title,
  onClose,
  onSubmit
}: {
  initial: PropertyRequest;
  title: string;
  onClose: () => void;
  onSubmit: (body: PropertyRequest) => Promise<void>;
}) {
  const [form, setForm] = useState<PropertyRequest>(initial);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  function set<K extends keyof PropertyRequest>(key: K, value: PropertyRequest[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await onSubmit({ ...form, totalUnits: Number(form.totalUnits) });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Save failed");
      setBusy(false);
    }
  }

  return (
    <Modal title={title} onClose={onClose}>
      <form className="form-grid" onSubmit={submit}>
        <Field label="Name">
          <TextInput value={form.name} onChange={(e) => set("name", e.target.value)} required />
        </Field>
        <Field label="Description">
          <TextArea value={form.description ?? ""} onChange={(e) => set("description", e.target.value)} />
        </Field>
        <div className="form-row">
          <Field label="Address line 1">
            <TextInput
              value={form.addressLine1}
              onChange={(e) => set("addressLine1", e.target.value)}
              required
            />
          </Field>
          <Field label="Address line 2">
            <TextInput
              value={form.addressLine2 ?? ""}
              onChange={(e) => set("addressLine2", e.target.value)}
            />
          </Field>
        </div>
        <div className="form-row">
          <Field label="City">
            <TextInput value={form.city} onChange={(e) => set("city", e.target.value)} required />
          </Field>
          <Field label="State">
            <TextInput value={form.state} onChange={(e) => set("state", e.target.value)} required />
          </Field>
        </div>
        <div className="form-row">
          <Field label="Postal code">
            <TextInput
              value={form.postalCode}
              onChange={(e) => set("postalCode", e.target.value)}
              required
            />
          </Field>
          <Field label="Country">
            <TextInput value={form.country} onChange={(e) => set("country", e.target.value)} required />
          </Field>
        </div>
        <div className="form-row">
          <Field label="Type">
            <Select
              options={TYPES}
              value={form.propertyType}
              onChange={(e) => set("propertyType", e.target.value as PropertyType)}
            />
          </Field>
          <Field label="Total units">
            <TextInput
              type="number"
              min={1}
              value={form.totalUnits}
              onChange={(e) => set("totalUnits", Number(e.target.value))}
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
            {busy ? "Saving…" : "Save"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
