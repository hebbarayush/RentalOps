import React from "react";
import ReactDOM from "react-dom/client";
import { createBrowserRouter, Navigate, RouterProvider } from "react-router-dom";
import { App } from "./App";
import { AuthProvider, useAuth } from "./auth/AuthContext";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { DashboardPage } from "./pages/DashboardPage";
import { ForgotPasswordPage } from "./pages/ForgotPasswordPage";
import { LeasesPage } from "./pages/LeasesPage";
import { LoginPage } from "./pages/LoginPage";
import { MaintenancePage } from "./pages/MaintenancePage";
import { PaymentsPage } from "./pages/PaymentsPage";
import { PropertiesPage } from "./pages/PropertiesPage";
import { RegisterPage } from "./pages/RegisterPage";
import { ResetPasswordPage } from "./pages/ResetPasswordPage";
import { TenantHomePage } from "./pages/TenantHomePage";
import { TenantsPage } from "./pages/TenantsPage";
import { UsersPage } from "./pages/UsersPage";
import "./styles.css";

const MANAGER = ["ADMIN", "PROPERTY_MANAGER"] as const;

function HomeRoute() {
  const { isTenant } = useAuth();
  return isTenant ? <TenantHomePage /> : <DashboardPage />;
}

function managerRoute(element: React.ReactNode) {
  return <ProtectedRoute roles={[...MANAGER]}>{element}</ProtectedRoute>;
}

const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  { path: "/register", element: <RegisterPage /> },
  { path: "/forgot-password", element: <ForgotPasswordPage /> },
  { path: "/reset-password", element: <ResetPasswordPage /> },
  {
    path: "/",
    element: (
      <ProtectedRoute>
        <App />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <HomeRoute /> },
      { path: "properties", element: managerRoute(<PropertiesPage />) },
      { path: "tenants", element: managerRoute(<TenantsPage />) },
      { path: "leases", element: managerRoute(<LeasesPage />) },
      { path: "payments", element: <PaymentsPage /> },
      { path: "maintenance", element: <MaintenancePage /> },
      {
        path: "users",
        element: (
          <ProtectedRoute roles={["ADMIN"]}>
            <UsersPage />
          </ProtectedRoute>
        )
      }
    ]
  },
  { path: "*", element: <Navigate to="/" replace /> }
]);

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  </React.StrictMode>
);
