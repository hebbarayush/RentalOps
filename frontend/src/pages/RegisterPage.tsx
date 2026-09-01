import { useState } from "react";
import type { FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../lib/api";
import { Button, Field, Select, TextInput } from "../components/ui";
import type { RegisterRequest } from "../types";

export function RegisterPage() {
  const { user, register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState<RegisterRequest>({
    fullName: "",
    email: "",
    password: "",
    phone: "",
    role: "PROPERTY_MANAGER"
  });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/" replace />;

  function set<K extends keyof RegisterRequest>(key: K, value: RegisterRequest[K]) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      await register({ ...form, phone: form.phone || null });
      navigate("/", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Registration failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-page">
      <form className="auth-card" onSubmit={handleSubmit}>
        <div className="auth-brand">
          <div className="brand-mark">R</div>
          <span>RentalOps</span>
        </div>
        <h1>Create account</h1>
        <Field label="Full name">
          <TextInput value={form.fullName} onChange={(e) => set("fullName", e.target.value)} required />
        </Field>
        <Field label="Email">
          <TextInput
            type="email"
            value={form.email}
            onChange={(e) => set("email", e.target.value)}
            required
          />
        </Field>
        <Field label="Password (min 8 characters)">
          <TextInput
            type="password"
            value={form.password}
            onChange={(e) => set("password", e.target.value)}
            minLength={8}
            required
          />
        </Field>
        <Field label="Phone (optional)">
          <TextInput value={form.phone ?? ""} onChange={(e) => set("phone", e.target.value)} />
        </Field>
        <Field label="Role">
          <Select
            options={["PROPERTY_MANAGER", "TENANT"]}
            value={form.role}
            onChange={(e) => set("role", e.target.value as RegisterRequest["role"])}
          />
        </Field>
        {error && <div className="form-error">{error}</div>}
        <Button type="submit" disabled={busy}>
          {busy ? "Creating…" : "Create account"}
        </Button>
        <p className="auth-alt">
          Already have an account? <Link to="/login">Sign in</Link>
        </p>
      </form>
    </main>
  );
}
