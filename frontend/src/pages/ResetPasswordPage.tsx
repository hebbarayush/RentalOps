import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { ApiError } from "../lib/api";
import { authApi } from "../lib/resources";
import { BrandMark } from "../components/BrandMark";
import { Button, Field, TextInput } from "../components/ui";

export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const [token, setToken] = useState(params.get("token") ?? "");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await authApi.resetPassword(token, password);
      setDone(true);
      setTimeout(() => navigate("/login", { replace: true }), 1500);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Reset failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-page">
      <form className="auth-card" onSubmit={submit}>
        <div className="auth-brand">
          <BrandMark />
          <span>RentalOps</span>
        </div>
        <h1>Choose a new password</h1>
        {done ? (
          <p className="auth-hint">Password updated. Redirecting to sign in…</p>
        ) : (
          <>
            <Field label="Reset token">
              <TextInput value={token} onChange={(e) => setToken(e.target.value)} required />
            </Field>
            <Field label="New password (min 8 characters)">
              <TextInput
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                minLength={8}
                required
              />
            </Field>
            {error && <div className="form-error">{error}</div>}
            <Button type="submit" disabled={busy}>
              {busy ? "Saving…" : "Update password"}
            </Button>
          </>
        )}
        <p className="auth-alt">
          <Link to="/login">Back to sign in</Link>
        </p>
      </form>
    </main>
  );
}
