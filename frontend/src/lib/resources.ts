import { api } from "./api";
import type {
  AdminUserRequest,
  DashboardSummaryResponse,
  LeaseRenewalRequest,
  LeaseRequest,
  LeaseResponse,
  MaintenanceCreateRequest,
  MaintenanceResponse,
  MaintenanceUpdateRequest,
  MarkPaymentRequest,
  NotificationResponse,
  Page,
  PropertyRequest,
  PropertyResponse,
  ReliabilityResponse,
  RentAtRiskResponse,
  RentPaymentRequest,
  RentPaymentResponse,
  TenantRequest,
  TenantResponse,
  UserResponse
} from "../types";

export interface ListParams {
  page?: number;
  size?: number;
  sort?: string;
  [key: string]: string | number | boolean | undefined;
}

function qs(params: ListParams = {}): string {
  const sp = new URLSearchParams();
  const withDefaults = { page: 0, size: 20, sort: "id,desc", ...params };
  for (const [k, v] of Object.entries(withDefaults)) {
    if (v !== undefined && v !== "" && v !== null) sp.set(k, String(v));
  }
  return `?${sp.toString()}`;
}

export const dashboardApi = {
  summary: () => api.get<DashboardSummaryResponse>("/api/dashboard/summary"),
  rentAtRisk: () => api.get<RentAtRiskResponse>("/api/dashboard/rent-at-risk")
};

export const propertiesApi = {
  list: (p?: ListParams) => api.get<Page<PropertyResponse>>(`/api/properties${qs(p)}`),
  create: (body: PropertyRequest) => api.post<PropertyResponse>("/api/properties", body),
  update: (id: number, body: PropertyRequest) => api.put<PropertyResponse>(`/api/properties/${id}`, body),
  deactivate: (id: number) => api.delete<void>(`/api/properties/${id}`)
};

export const tenantsApi = {
  list: (p?: ListParams) => api.get<Page<TenantResponse>>(`/api/tenants${qs(p)}`),
  me: () => api.get<TenantResponse>("/api/tenants/me"),
  reliability: (id: number) => api.get<ReliabilityResponse>(`/api/tenants/${id}/reliability`),
  create: (body: TenantRequest) => api.post<TenantResponse>("/api/tenants", body),
  update: (id: number, body: TenantRequest) => api.put<TenantResponse>(`/api/tenants/${id}`, body)
};

export const leasesApi = {
  list: (p?: ListParams) => api.get<Page<LeaseResponse>>(`/api/leases${qs(p)}`),
  create: (body: LeaseRequest) => api.post<LeaseResponse>("/api/leases", body),
  update: (id: number, body: LeaseRequest) => api.put<LeaseResponse>(`/api/leases/${id}`, body),
  activate: (id: number) => api.post<LeaseResponse>(`/api/leases/${id}/activate`),
  terminate: (id: number) => api.post<LeaseResponse>(`/api/leases/${id}/terminate`),
  renew: (id: number, body: LeaseRenewalRequest) => api.post<LeaseResponse>(`/api/leases/${id}/renew`, body),
  generateCharges: (id: number) => api.post<{ created: number }>(`/api/leases/${id}/generate-charges`)
};

export const paymentsApi = {
  list: (p?: ListParams) => api.get<Page<RentPaymentResponse>>(`/api/rent-payments${qs(p)}`),
  create: (body: RentPaymentRequest) => api.post<RentPaymentResponse>("/api/rent-payments", body),
  markPaid: (id: number, body: MarkPaymentRequest) =>
    api.post<RentPaymentResponse>(`/api/rent-payments/${id}/mark-paid`, body),
  runBilling: () => api.post<{ created: number }>("/api/rent-payments/run-billing")
};

export const maintenanceApi = {
  list: (p?: ListParams) => api.get<Page<MaintenanceResponse>>(`/api/maintenance-requests${qs(p)}`),
  create: (body: MaintenanceCreateRequest) =>
    api.post<MaintenanceResponse>("/api/maintenance-requests", body),
  update: (id: number, body: MaintenanceUpdateRequest) =>
    api.put<MaintenanceResponse>(`/api/maintenance-requests/${id}`, body),
  retriage: (id: number) => api.post<MaintenanceResponse>(`/api/maintenance-requests/${id}/retriage`),
  acceptSuggestion: (id: number) =>
    api.post<MaintenanceResponse>(`/api/maintenance-requests/${id}/accept-suggestion`)
};

export const usersApi = {
  list: (p?: ListParams) => api.get<Page<UserResponse>>(`/api/users${qs(p)}`),
  create: (body: AdminUserRequest) => api.post<UserResponse>("/api/users", body),
  setStatus: (id: number, value: "ACTIVE" | "DISABLED") =>
    api.patch<UserResponse>(`/api/users/${id}/status?value=${value}`),
  resetPassword: (id: number) => api.post<{ temporaryPassword: string }>(`/api/users/${id}/reset-password`)
};

export const notificationsApi = {
  list: (p?: ListParams) => api.get<Page<NotificationResponse>>(`/api/notifications${qs(p)}`),
  unreadCount: () => api.get<{ count: number }>("/api/notifications/unread-count"),
  markRead: (id: number) => api.post<void>(`/api/notifications/${id}/read`),
  markAllRead: () => api.post<{ updated: number }>("/api/notifications/read-all")
};

export const authApi = {
  forgotPassword: (email: string) =>
    api.post<{ message: string; devToken?: string }>("/api/auth/forgot-password", { email }),
  resetPassword: (token: string, newPassword: string) =>
    api.post<{ message: string }>("/api/auth/reset-password", { token, newPassword })
};
