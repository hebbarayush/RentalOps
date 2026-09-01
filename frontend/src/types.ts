// Mirrors the backend DTO records in backend/src/main/java/com/rentalops/**

export type RoleName = "ADMIN" | "PROPERTY_MANAGER" | "TENANT";

export type UserStatus = "ACTIVE" | "DISABLED";

export interface UserResponse {
  id: number;
  fullName: string;
  email: string;
  phone: string | null;
  roles: RoleName[];
  status: UserStatus;
  createdAt: string;
}

export interface AdminUserRequest {
  fullName: string;
  email: string;
  password: string;
  phone?: string | null;
  role: RoleName;
}

export interface AuthResponse {
  token: string;
  user: UserResponse;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  details: string[];
}

export type PropertyType = "APARTMENT" | "HOUSE" | "COMMERCIAL" | "VILLA" | "STUDIO";
export type PropertyStatus = "ACTIVE" | "INACTIVE";

export interface PropertyResponse {
  id: number;
  managerId: number;
  name: string;
  description: string | null;
  addressLine1: string;
  addressLine2: string | null;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  propertyType: PropertyType;
  totalUnits: number;
  occupiedUnits: number;
  status: PropertyStatus;
}

export interface PropertyRequest {
  name: string;
  description?: string | null;
  addressLine1: string;
  addressLine2?: string | null;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  propertyType: PropertyType;
  totalUnits: number;
}

export type TenantStatus = "ACTIVE" | "INACTIVE" | "PENDING";

export interface TenantResponse {
  id: number;
  managerId: number;
  userId: number | null;
  fullName: string;
  email: string;
  phone: string;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  governmentIdNumber: string | null;
  status: TenantStatus;
}

export interface TenantRequest {
  fullName: string;
  email: string;
  phone: string;
  emergencyContactName?: string | null;
  emergencyContactPhone?: string | null;
  governmentIdNumber?: string | null;
  status: TenantStatus;
}

export type LeaseStatus = "DRAFT" | "ACTIVE" | "EXPIRED" | "TERMINATED";

export interface LeaseResponse {
  id: number;
  propertyId: number;
  tenantId: number;
  unitNumber: string;
  startDate: string;
  endDate: string;
  monthlyRent: number;
  securityDeposit: number;
  leaseStatus: LeaseStatus;
  agreementFileUrl: string | null;
  billingDayOfMonth: number;
  nextChargeDate: string | null;
  renewedFromLeaseId: number | null;
}

export interface LeaseRenewalRequest {
  startDate?: string | null;
  endDate: string;
  monthlyRent?: number | null;
  securityDeposit?: number | null;
}

export interface LeaseRequest {
  propertyId: number;
  tenantId: number;
  unitNumber: string;
  startDate: string;
  endDate: string;
  monthlyRent: number;
  securityDeposit: number;
  agreementFileUrl?: string | null;
}

export type PaymentStatus = "PENDING" | "PAID" | "OVERDUE" | "FAILED" | "PARTIAL";
export type PaymentMethod = "CASH" | "BANK_TRANSFER" | "CARD" | "UPI" | "OTHER";

export interface RentPaymentResponse {
  id: number;
  leaseId: number;
  tenantId: number;
  propertyId: number;
  amountDue: number;
  amountPaid: number;
  dueDate: string;
  paidDate: string | null;
  paymentStatus: PaymentStatus;
  paymentMethod: PaymentMethod | null;
  transactionReference: string | null;
  notes: string | null;
}

export interface RentPaymentRequest {
  leaseId: number;
  amountDue: number;
  dueDate: string;
  notes?: string | null;
}

export interface MarkPaymentRequest {
  amountPaid: number;
  paymentMethod: PaymentMethod;
  transactionReference?: string | null;
}

export type MaintenancePriority = "LOW" | "MEDIUM" | "HIGH" | "URGENT";
export type MaintenanceStatus = "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";

export interface MaintenanceTriage {
  triaged: boolean;
  source: "CLAUDE" | "RULES" | null;
  category: string | null;
  suggestedPriority: MaintenancePriority | null;
  summary: string | null;
  costBand: string | null;
  draftReply: string | null;
}

export interface MaintenanceResponse {
  id: number;
  tenantId: number;
  propertyId: number;
  title: string;
  description: string;
  priority: MaintenancePriority;
  status: MaintenanceStatus;
  managerNotes: string | null;
  resolvedAt: string | null;
  triage: MaintenanceTriage;
}

export interface MaintenanceCreateRequest {
  tenantId: number;
  propertyId: number;
  title: string;
  description: string;
  priority: MaintenancePriority;
}

export interface MaintenanceUpdateRequest {
  status: MaintenanceStatus;
  managerNotes?: string | null;
}

export interface RegisterRequest {
  fullName: string;
  email: string;
  password: string;
  phone?: string | null;
  role: Exclude<RoleName, "ADMIN">;
}

export type ReliabilityBand = "NEW" | "EXCELLENT" | "GOOD" | "FAIR" | "POOR";

export interface ReliabilityResponse {
  tenantId: number;
  tenantName: string;
  totalCharges: number;
  onTimeCount: number;
  lateCount: number;
  currentlyOverdueCount: number;
  avgDaysLate: number;
  score: number;
  band: ReliabilityBand;
  predictedLateRisk: boolean;
  outstanding: number;
  nextDueDate: string | null;
}

export interface RentAtRiskResponse {
  tenantsAtRisk: number;
  exposureAmount: number;
  tenants: ReliabilityResponse[];
}

export type NotificationType =
  | "RENT_DUE"
  | "RENT_OVERDUE"
  | "RENT_PAID"
  | "LEASE_ACTIVATED"
  | "LEASE_EXPIRING"
  | "LEASE_EXPIRED"
  | "MAINTENANCE_CREATED"
  | "MAINTENANCE_UPDATED"
  | "ACCOUNT"
  | "GENERAL";

export interface NotificationResponse {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  linkType: string | null;
  linkId: number | null;
  read: boolean;
  createdAt: string;
}

export interface DashboardSummaryResponse {
  totalProperties: number;
  totalUnits: number;
  occupiedUnits: number;
  vacantUnits: number;
  activeTenants: number;
  activeLeases: number;
  leasesExpiringThisMonth: number;
  rentExpected: number;
  rentCollected: number;
  pendingRent: number;
  openMaintenanceRequests: number;
}
