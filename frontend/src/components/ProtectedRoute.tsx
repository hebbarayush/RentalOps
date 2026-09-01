import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import type { RoleName } from "../types";

export function ProtectedRoute({
  children,
  roles
}: {
  children: ReactNode;
  roles?: RoleName[];
}) {
  const { user, loading, hasRole } = useAuth();
  const location = useLocation();

  if (loading) {
    return <div className="route-loading">Loading…</div>;
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (roles && !hasRole(...roles)) {
    return (
      <div className="empty-state">
        <h3>Not authorised</h3>
        <p>Your account does not have access to this area.</p>
      </div>
    );
  }

  return <>{children}</>;
}
