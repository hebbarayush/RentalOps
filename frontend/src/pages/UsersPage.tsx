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
import { formatDate, titleCase } from "../lib/format";
import { usersApi } from "../lib/resources";
import { useListView } from "../lib/useListView";
import type { AdminUserRequest, RoleName, UserResponse } from "../types";

const CREATE_ROLES: RoleName[] = ["PROPERTY_MANAGER", "ADMIN"];

export function UsersPage() {
  const list = useListView((params) => usersApi.list(params));
  const [creating, setCreating] = useState(false);
  const [tempPassword, setTempPassword] = useState<{ email: string; password: string } | null>(null);
  const [error, setError] = useState("");

  async function setStatus(u: UserResponse, value: "ACTIVE" | "DISABLED") {
    setError("");
    try {
      await usersApi.setStatus(u.id, value);
      list.reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not update status");
    }
  }

  async function resetPassword(u: UserResponse) {
    setError("");
    try {
      const r = await usersApi.resetPassword(u.id);
      setTempPassword({ email: u.email, password: r.temporaryPassword });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Could not reset password");
    }
  }

  return (
    <section className="page">
      <PageHeader
        title="Users"
        subtitle="Manage staff accounts. Tenant accounts are created from the tenant record or self-registration."
        actions={<Button onClick={() => setCreating(true)}>New user</Button>}
      />

      <FilterBar>
        <TextInput
          placeholder="Search name / email"
          defaultValue={list.filters.q ?? ""}
          onChange={(e) => list.setFilter("q", e.target.value)}
        />
        <select className="input" value={list.filters.role ?? ""} onChange={(e) => list.setFilter("role", e.target.value)}>
          <option value="">Any role</option>
          {["ADMIN", "PROPERTY_MANAGER", "TENANT"].map((r) => (
            <option key={r} value={r}>
              {titleCase(r)}
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
          <option value="DISABLED">Disabled</option>
        </select>
      </FilterBar>

      {error && <div className="form-error">{error}</div>}

      <DataState
        loading={list.loading}
        error={list.error}
        empty={list.rows.length === 0}
        emptyMessage="No users match."
      >
        <Table columns={["Name", "Email", "Roles", "Status", "Joined", ""]}>
          {list.rows.map((u) => (
            <tr key={u.id}>
              <td>
                <strong>{u.fullName}</strong>
              </td>
              <td>{u.email}</td>
              <td>{u.roles.map(titleCase).join(", ")}</td>
              <td>
                <StatusPill value={u.status} />
              </td>
              <td>{formatDate(u.createdAt)}</td>
              <td className="row-actions">
                {u.status === "ACTIVE" ? (
                  <Button variant="danger" onClick={() => setStatus(u, "DISABLED")}>
                    Disable
                  </Button>
                ) : (
                  <Button variant="secondary" onClick={() => setStatus(u, "ACTIVE")}>
                    Enable
                  </Button>
                )}
                <Button variant="ghost" onClick={() => resetPassword(u)}>
                  Reset password
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

      {creating && (
        <UserForm
          onClose={() => setCreating(false)}
          onSubmit={async (body) => {
            await usersApi.create(body);
            setCreating(false);
            list.reload();
          }}
        />
      )}

      {tempPassword && (
        <Modal title="Temporary password" onClose={() => setTempPassword(null)}>
          <p>
            Share this one-time password with <strong>{tempPassword.email}</strong>. It replaces their
            current password immediately.
          </p>
          <pre className="token-box">{tempPassword.password}</pre>
        </Modal>
      )}
    </section>
  );
}

function UserForm({
  onClose,
  onSubmit
}: {
  onClose: () => void;
  onSubmit: (body: AdminUserRequest) => Promise<void>;
}) {
  const [form, setForm] = useState<AdminUserRequest>({
    fullName: "",
    email: "",
    password: "",
    phone: "",
    role: "PROPERTY_MANAGER"
  });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  function set<K extends keyof AdminUserRequest>(key: K, value: AdminUserRequest[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await onSubmit({ ...form, phone: form.phone || null });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Save failed");
      setBusy(false);
    }
  }

  return (
    <Modal title="New user" onClose={onClose}>
      <form className="form-grid" onSubmit={submit}>
        <Field label="Full name">
          <TextInput value={form.fullName} onChange={(e) => set("fullName", e.target.value)} required />
        </Field>
        <div className="form-row">
          <Field label="Email">
            <TextInput type="email" value={form.email} onChange={(e) => set("email", e.target.value)} required />
          </Field>
          <Field label="Phone (optional)">
            <TextInput value={form.phone ?? ""} onChange={(e) => set("phone", e.target.value)} />
          </Field>
        </div>
        <div className="form-row">
          <Field label="Temporary password (min 8)">
            <TextInput
              value={form.password}
              onChange={(e) => set("password", e.target.value)}
              minLength={8}
              required
            />
          </Field>
          <Field label="Role">
            <Select
              options={CREATE_ROLES}
              value={form.role}
              onChange={(e) => set("role", e.target.value as RoleName)}
            />
          </Field>
        </div>
        {error && <div className="form-error">{error}</div>}
        <div className="form-actions">
          <Button type="button" variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={busy}>
            {busy ? "Creating…" : "Create user"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
