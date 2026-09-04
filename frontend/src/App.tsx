import { Building2, FileText, Home, IndianRupee, LogOut, ShieldCheck, Users, Wrench } from "lucide-react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import { BrandMark } from "./components/BrandMark";
import { NotificationBell } from "./components/NotificationBell";
import type { RoleName } from "./types";

interface NavItem {
  to: string;
  label: string;
  icon: typeof Home;
  end?: boolean;
  roles?: RoleName[];
}

const MANAGER: RoleName[] = ["ADMIN", "PROPERTY_MANAGER"];

function initials(name?: string | null): string {
  const parts = name?.trim().split(/\s+/).filter(Boolean) ?? [];
  if (parts.length === 0) return "?";
  return (parts[0][0] + (parts[1]?.[0] ?? "")).toUpperCase();
}

const items: NavItem[] = [
  { to: "/", label: "Dashboard", icon: Home, end: true, roles: MANAGER },
  { to: "/", label: "My home", icon: Home, end: true, roles: ["TENANT"] },
  { to: "/properties", label: "Properties", icon: Building2, roles: MANAGER },
  { to: "/tenants", label: "Tenants", icon: Users, roles: MANAGER },
  { to: "/leases", label: "Leases", icon: FileText, roles: MANAGER },
  { to: "/payments", label: "Rent", icon: IndianRupee },
  { to: "/maintenance", label: "Maintenance", icon: Wrench },
  { to: "/users", label: "Users", icon: ShieldCheck, roles: ["ADMIN"] }
];

export function App() {
  const { user, logout, hasRole } = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    logout();
    navigate("/login", { replace: true });
  }

  const visible = items.filter((l) => !l.roles || hasRole(...l.roles));

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <BrandMark />
          <strong>RentalOps</strong>
        </div>
        <nav>
          {visible.map((link) => {
            const Icon = link.icon;
            return (
              <NavLink
                key={link.label}
                to={link.to}
                end={link.end}
                className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}
              >
                <Icon size={16} strokeWidth={2} />
                {link.label}
              </NavLink>
            );
          })}
        </nav>
        <div className="sidebar-foot">
          <span className="sidebar-avatar" aria-hidden="true">
            {initials(user?.fullName)}
          </span>
          <div>
            <strong>{user?.fullName}</strong>
            <span>{user?.email}</span>
          </div>
        </div>
      </aside>
      <main className="main-panel">
        <header className="ledge">
          <NotificationBell />
          <button className="ledge-signout" onClick={handleLogout} title="Sign out">
            <LogOut size={15} strokeWidth={2} />
            <span>Sign out</span>
          </button>
        </header>
        <Outlet />
      </main>
    </div>
  );
}
