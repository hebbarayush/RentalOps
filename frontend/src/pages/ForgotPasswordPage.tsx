import { useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../lib/api";
import { authApi } from "../lib/resources";
import { BrandMark } from "../components/BrandMark";
import { Button, Field, TextInput } from "../components/ui";

export function ForgotPasswordPage() {
  const [email, setEmail] = useState("");
  const [message, setMessage] = useState("");
  const [devToken, setDevToken] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage("");
    setDevToken("");
    try {
      const r = await authApi.forgotPassword(email);
      setMessage(r.message);
      if (r.devToken) setDevToken(r.devToken);
    } catch (err) {
      setMessage(err instanceof ApiError ? err.message : "Something went wrong");
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
        <h1>Reset your password</h1>
        <Field label="Email">
          <TextInput type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </Field>
        <Button type="submit" disabled={busy}>
          {busy ? "Sending…" : "Send reset link"}
        </Button>
        {message && <p className="auth-hint">{message}</p>}
        {devToken && (
          <p className="auth-hint">
            No mail server configured — use this token:{" "}
            <Link to={`/reset-password?token=${encodeURIComponent(devToken)}`}>continue</Link>
          </p>
        )}
        <p className="auth-alt">
          <Link to="/login">Back to sign in</Link>
        </p>
      </form>
    </main>
  );
}
