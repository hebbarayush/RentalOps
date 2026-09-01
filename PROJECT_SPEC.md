# RentalOps Project Specification

## 1. Product Overview

RentalOps is a full-stack property and rental management SaaS application for landlords, property managers, and tenants.

This is not a property listing marketplace like NoBroker. The product is a B2B/B2C operations platform where property managers can manage properties, tenants, leases, rent payments, maintenance requests, reminders, documents, and dashboard analytics from one system.

The goal is to build a production-style Java full-stack project that demonstrates:

- Spring Boot backend architecture
- REST API design
- Authentication and role-based authorization
- Relational database modeling
- JPA/Hibernate usage
- Frontend integration
- Business workflows beyond simple CRUD
- Docker-based local development
- Cloud deployment readiness
- Clean README and portfolio presentation

## 2. Target Users

### Admin

The system owner or superuser.

Responsibilities:

- Manage users
- View all managers and tenants
- Monitor system-level metrics
- Access all properties and requests

### Property Manager

The main business user.

Responsibilities:

- Add and manage properties
- Add tenants
- Assign tenants to properties or units
- Create leases
- Track rent payments
- View overdue payments
- Resolve maintenance requests
- View business dashboard

### Tenant

The resident or renter.

Responsibilities:

- Log in to tenant portal
- View assigned property
- View lease information
- View rent payment status
- Submit maintenance requests
- Track request status

## 3. Recommended Tech Stack

### Backend

- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA
- Hibernate
- Spring Security
- JWT authentication
- Bean Validation
- Maven
- Lombok, optional
- MapStruct, optional
- Swagger/OpenAPI

### Database

Use PostgreSQL or MySQL as the main database.

Recommended: PostgreSQL.

Reason:

- RentalOps has strongly relational data.
- Properties, tenants, leases, payments, users, and maintenance requests are connected.
- SQL constraints help protect data correctness.
- Transactions are important for payment and lease workflows.
- PostgreSQL gives a strong path toward analytics and future geospatial features.

MySQL is also acceptable if the learning path specifically requires it.



### Frontend

Recommended: React with TypeScript.

Reason:

- React has broad industry adoption across product companies, startups, and large tech teams.
- React is strong for portfolio visibility because many recruiters and hiring teams recognize it immediately.
- TypeScript adds type safety and makes the frontend feel more production-ready.
- React works very well with Spring Boot because the backend exposes REST APIs and the frontend consumes them through HTTP.
- The ecosystem has excellent libraries for routing, forms, tables, charts, and dashboard UI.

Angular is also a valid Spring Boot frontend choice, especially in enterprise Java environments. It is not a bad combination. For this project, React is preferred because it has wider market visibility and is more flexible for a modern portfolio application.

Suggested frontend libraries:

- React
- TypeScript
- Vite
- React Router
- TanStack Query
- React Hook Form
- Zod
- Tailwind CSS or Material UI
- Recharts

### Infrastructure

- Docker
- Docker Compose
- GitHub Actions
- AWS EC2 for backend deployment
- AWS RDS for database
- Netlify, Vercel, S3, or Nginx for frontend deployment

## 4. High-Level Architecture

Recommended architecture for MVP:

```text
React Frontend
      |
      | HTTPS / REST JSON
      v
Spring Boot Backend
      |
      | JPA / Hibernate
      v
PostgreSQL or MySQL
```

Optional later architecture:

```text
React Frontend
      |
Spring Boot API
      |
PostgreSQL / MySQL
      |
Redis for caching and reminders
      |
MongoDB for audit logs or notifications
      |
Cloud storage for images and documents
```

## 5. Backend Package Structure

Use a feature-oriented package structure:

```text
com.rentalops
  RentalOpsApplication.java

  auth
    AuthController.java
    AuthService.java
    JwtService.java
    LoginRequest.java
    RegisterRequest.java
    AuthResponse.java

  config
    SecurityConfig.java
    OpenApiConfig.java
    CorsConfig.java

  user
    User.java
    Role.java
    UserRepository.java
    UserService.java
    UserController.java

  property
    Property.java
    PropertyRepository.java
    PropertyService.java
    PropertyController.java
    dto

  tenant
    Tenant.java
    TenantRepository.java
    TenantService.java
    TenantController.java
    dto

  lease
    Lease.java
    LeaseRepository.java
    LeaseService.java
    LeaseController.java
    dto

  payment
    RentPayment.java
    RentPaymentRepository.java
    RentPaymentService.java
    RentPaymentController.java
    dto

  maintenance
    MaintenanceRequest.java
    MaintenanceRepository.java
    MaintenanceService.java
    MaintenanceController.java
    dto

  dashboard
    DashboardController.java
    DashboardService.java
    DashboardSummaryResponse.java

  common
    ApiError.java
    GlobalExceptionHandler.java
    PageResponse.java
    BaseEntity.java
```

## 6. Core Database Model

### users

Stores all application users.

Fields:

- id
- full_name
- email
- password_hash
- phone
- status
- created_at
- updated_at

Status values:

- ACTIVE
- DISABLED

### roles

Fields:

- id
- name

Role values:

- ADMIN
- PROPERTY_MANAGER
- TENANT

### user_roles

Many-to-many join table between users and roles.

Fields:

- user_id
- role_id

### properties

Fields:

- id
- manager_id
- name
- description
- address_line_1
- address_line_2
- city
- state
- postal_code
- country
- property_type
- total_units
- occupied_units
- status
- created_at
- updated_at

Property type values:

- APARTMENT
- HOUSE
- COMMERCIAL
- VILLA
- STUDIO

Status values:

- ACTIVE
- INACTIVE

### property_images

Fields:

- id
- property_id
- image_url
- caption
- uploaded_at

For MVP, image_url can be a string. Actual file upload can be added later.

### tenants

Fields:

- id
- user_id
- manager_id
- full_name
- email
- phone
- emergency_contact_name
- emergency_contact_phone
- government_id_number
- status
- created_at
- updated_at

Status values:

- ACTIVE
- INACTIVE
- PENDING

### leases

Fields:

- id
- property_id
- tenant_id
- unit_number
- start_date
- end_date
- monthly_rent
- security_deposit
- lease_status
- agreement_file_url
- created_at
- updated_at

Lease status values:

- DRAFT
- ACTIVE
- EXPIRED
- TERMINATED

### rent_payments

Fields:

- id
- lease_id
- tenant_id
- property_id
- amount_due
- amount_paid
- due_date
- paid_date
- payment_status
- payment_method
- transaction_reference
- notes
- created_at
- updated_at

Payment status values:

- PENDING
- PAID
- OVERDUE
- FAILED
- PARTIAL

Payment method values:

- CASH
- BANK_TRANSFER
- CARD
- UPI
- OTHER

### maintenance_requests

Fields:

- id
- tenant_id
- property_id
- title
- description
- priority
- status
- manager_notes
- created_at
- updated_at
- resolved_at

Priority values:

- LOW
- MEDIUM
- HIGH
- URGENT

Status values:

- OPEN
- IN_PROGRESS
- RESOLVED
- CLOSED

### notifications

Fields:

- id
- user_id
- title
- message
- notification_type
- read_status
- created_at

This can be implemented later after the main workflows are stable.

## 7. API Design

All protected APIs should require a JWT token.

Use this base path:

```text
/api
```

### Auth APIs

```text
POST /api/auth/register
POST /api/auth/login
GET  /api/auth/me
```

Register request:

```json
{
  "fullName": "Ayush Hebbar",
  "email": "ayush@example.com",
  "password": "StrongPassword123",
  "phone": "9999999999",
  "role": "PROPERTY_MANAGER"
}
```

Login request:

```json
{
  "email": "ayush@example.com",
  "password": "StrongPassword123"
}
```

Auth response:

```json
{
  "token": "jwt-token",
  "user": {
    "id": 1,
    "fullName": "Ayush Hebbar",
    "email": "ayush@example.com",
    "roles": ["PROPERTY_MANAGER"]
  }
}
```

### Property APIs

```text
POST   /api/properties
GET    /api/properties
GET    /api/properties/{id}
PUT    /api/properties/{id}
DELETE /api/properties/{id}
GET    /api/properties/search?city=&status=&type=&page=&size=
```

Rules:

- Admin can view all properties.
- Property manager can view only their own properties.
- Tenant can view only the property attached to their active lease.

### Tenant APIs

```text
POST   /api/tenants
GET    /api/tenants
GET    /api/tenants/{id}
PUT    /api/tenants/{id}
DELETE /api/tenants/{id}
GET    /api/tenants/search?name=&status=&page=&size=
```

Rules:

- Admin can view all tenants.
- Property manager can view tenants connected to their properties.
- Tenant can view only their own profile.

### Lease APIs

```text
POST /api/leases
GET  /api/leases
GET  /api/leases/{id}
PUT  /api/leases/{id}
POST /api/leases/{id}/activate
POST /api/leases/{id}/terminate
GET  /api/leases/expiring-soon
```

Rules:

- Only Admin and Property Manager can create or update leases.
- Tenant can view their own lease.

### Rent Payment APIs

```text
POST /api/rent-payments
GET  /api/rent-payments
GET  /api/rent-payments/{id}
PUT  /api/rent-payments/{id}
POST /api/rent-payments/{id}/mark-paid
GET  /api/rent-payments/overdue
GET  /api/rent-payments/tenant/{tenantId}
```

Rules:

- Property manager can create rent records for their tenants.
- Tenant can view their own rent records.
- Payment gateway integration is optional for MVP. A manual "mark paid" flow is enough initially.

### Maintenance APIs

```text
POST /api/maintenance-requests
GET  /api/maintenance-requests
GET  /api/maintenance-requests/{id}
PUT  /api/maintenance-requests/{id}
POST /api/maintenance-requests/{id}/assign
POST /api/maintenance-requests/{id}/resolve
POST /api/maintenance-requests/{id}/close
```

Rules:

- Tenant can create requests.
- Tenant can view their own requests.
- Property manager can view and update requests for their properties.
- Admin can view all requests.

### Dashboard APIs

```text
GET /api/dashboard/summary
GET /api/dashboard/revenue
GET /api/dashboard/occupancy
GET /api/dashboard/maintenance
```

Dashboard summary response:

```json
{
  "totalProperties": 12,
  "totalUnits": 84,
  "occupiedUnits": 67,
  "vacantUnits": 17,
  "activeTenants": 67,
  "monthlyRentExpected": 850000,
  "monthlyRentCollected": 725000,
  "pendingRent": 125000,
  "openMaintenanceRequests": 9,
  "leasesExpiringThisMonth": 4
}
```

## 8. Frontend Application Structure

Use React with TypeScript, Vite, React Router, and a feature-oriented folder structure.

Suggested structure:

```text
src
  app
    App.tsx
    router.tsx
    providers.tsx

  features
    auth
      LoginPage.tsx
      RegisterPage.tsx
      authApi.ts
      authStore.ts
    dashboard
      DashboardPage.tsx
      dashboardApi.ts
    properties
      PropertyListPage.tsx
      PropertyFormPage.tsx
      PropertyDetailPage.tsx
      propertyApi.ts
    tenants
      TenantListPage.tsx
      TenantFormPage.tsx
      TenantDetailPage.tsx
      tenantApi.ts
    leases
      LeaseListPage.tsx
      LeaseFormPage.tsx
      LeaseDetailPage.tsx
      leaseApi.ts
    payments
      PaymentListPage.tsx
      paymentApi.ts
    maintenance
      MaintenanceListPage.tsx
      MaintenanceFormPage.tsx
      MaintenanceDetailPage.tsx
      maintenanceApi.ts
    users

  shared
    api
      httpClient.ts
    components
    hooks
    layouts
    types
    validators
```

### Frontend Pages

Required MVP pages:

- Login
- Register
- Dashboard
- Properties list
- Add property
- Edit property
- Property detail
- Tenants list
- Add tenant
- Edit tenant
- Lease detail
- Rent payments list
- Maintenance requests list
- Create maintenance request
- User profile

Optional later pages:

- Admin users
- Reports
- Notifications
- Settings
- Subscription billing

## 9. Business Rules

### Property Rules

- A property belongs to one property manager.
- A property can have many tenants through leases.
- A property cannot be deleted if it has active leases.
- occupied_units must not exceed total_units.

### Tenant Rules

- A tenant can have one active lease at a time in the MVP.
- A tenant must have a linked user account if tenant login is enabled.
- A tenant can only access their own lease, payments, and requests.

### Lease Rules

- A lease must have a start date before end date.
- A lease cannot become ACTIVE unless tenant and property exist.
- A property unit should not have two active leases for the same period.
- Terminating a lease should update its status but should not delete history.

### Payment Rules

- A rent payment belongs to one lease.
- A payment is OVERDUE if due_date is before today and status is still PENDING or PARTIAL.
- amount_paid must not be negative.
- Marking a payment as PAID should set paid_date.

### Maintenance Rules

- Tenant can create requests only for their assigned property.
- Property manager can update status.
- Tenant can close a resolved request.
- resolved_at is set when status becomes RESOLVED.

## 10. Implementation Stages

Build this project in stages. Do not try to implement every feature at once.

### Stage 0: Repository Setup

Goal: Create clean project structure.

Tasks:

- Create backend Spring Boot project.
- Create frontend React project with Vite and TypeScript.
- Add root README.
- Add `.gitignore`.
- Add Docker Compose with database.
- Add environment variable examples.

Suggested root structure:

```text
RentalOps-Project
  backend
  frontend
  docs
  docker-compose.yml
  README.md
  PROJECT_SPEC.md
  SUMMARY.md
```

Done when:

- Backend starts locally.
- Frontend starts locally.
- Database starts using Docker Compose.

### Stage 1: Backend Foundation

Goal: Build a stable Spring Boot API skeleton.

Tasks:

- Configure database connection.
- Add health endpoint.
- Add global exception handler.
- Add validation support.
- Add Swagger/OpenAPI.
- Create BaseEntity with id, createdAt, updatedAt.
- Configure CORS for frontend local development.

Done when:

- `GET /actuator/health` or equivalent works.
- Swagger UI opens.
- Invalid requests return clean JSON errors.

### Stage 2: Authentication And Authorization

Goal: Secure the system with JWT and roles.

Tasks:

- Create User and Role entities.
- Implement register API.
- Implement login API.
- Hash passwords using BCrypt.
- Generate JWT token after login.
- Add JWT filter.
- Add role-based access rules.
- Add `GET /api/auth/me`.

Done when:

- User can register.
- User can log in.
- Protected APIs reject missing/invalid tokens.
- Role checks work.

### Stage 3: Property Management

Goal: Managers can manage properties.

Tasks:

- Create Property entity.
- Create PropertyRepository.
- Create request/response DTOs.
- Implement create property.
- Implement list properties with pagination.
- Implement get property by id.
- Implement update property.
- Implement soft delete or deactivate property.
- Enforce manager ownership.

Done when:

- Manager can create and manage only their own properties.
- Admin can view all properties.
- Tenant cannot create properties.

### Stage 4: Tenant Management

Goal: Managers can create and manage tenants.

Tasks:

- Create Tenant entity.
- Link Tenant to User where needed.
- Link Tenant to Property Manager.
- Implement tenant CRUD APIs.
- Add search by name/email/status.
- Enforce manager ownership.

Done when:

- Manager can create tenants.
- Manager can view only their own tenants.
- Tenant can view their own profile.

### Stage 5: Lease Management

Goal: Represent rental agreements.

Tasks:

- Create Lease entity.
- Link Lease to Property and Tenant.
- Add lease status lifecycle.
- Implement create lease.
- Implement activate lease.
- Implement terminate lease.
- Implement list active leases.
- Implement expiring soon query.
- Validate date ranges.
- Prevent duplicate active leases for same unit.

Done when:

- Manager can create and activate leases.
- Tenant can view their own lease.
- Invalid lease dates are rejected.

### Stage 6: Rent Payment Tracking

Goal: Track monthly rent and overdue payments.

Tasks:

- Create RentPayment entity.
- Generate or manually create monthly rent payment records.
- Implement list payments.
- Implement mark as paid.
- Implement overdue payments endpoint.
- Add payment status calculation.
- Add dashboard aggregation queries.

Done when:

- Manager can see expected, collected, pending, and overdue rent.
- Tenant can see their own payment status.
- Marking paid updates amount, status, and paid date.

### Stage 7: Maintenance Requests

Goal: Tenants can report issues and managers can resolve them.

Tasks:

- Create MaintenanceRequest entity.
- Implement create request API for tenants.
- Implement list requests.
- Implement update status API.
- Implement resolve API.
- Enforce tenant/property access rules.

Done when:

- Tenant can create request.
- Manager can see requests for their properties.
- Status lifecycle works.

### Stage 8: Dashboard APIs

Goal: Expose useful operational metrics.

Tasks:

- Build summary metrics endpoint.
- Build occupancy metrics endpoint.
- Build revenue metrics endpoint.
- Build maintenance metrics endpoint.
- Use repository queries or service-level aggregation.

Done when:

- Dashboard returns real calculated values from database.
- Metrics respect role access.

### Stage 9: React Frontend Foundation

Goal: Create frontend shell and authentication flow.

Tasks:

- Create React app using Vite and TypeScript.
- Add routing.
- Add layout with sidebar/topbar.
- Add login page.
- Add register page.
- Store JWT securely enough for MVP.
- Add shared HTTP client.
- Add protected route component.
- Add role-based route component.

Done when:

- User can log in from frontend.
- Token is sent with API requests.
- Unauthorized users are redirected.

### Stage 10: React Core Screens

Goal: Implement business screens.

Tasks:

- Dashboard page.
- Properties list page.
- Property form page.
- Property detail page.
- Tenants list page.
- Tenant form page.
- Lease page.
- Rent payments page.
- Maintenance requests page.

Done when:

- Main user workflows are usable from browser.
- Forms validate required fields.
- Errors are shown clearly.

### Stage 11: Docker And Local Developer Experience

Goal: Make the project easy to run.

Tasks:

- Add backend Dockerfile.
- Add frontend Dockerfile, optional.
- Add Docker Compose for backend, frontend, and database.
- Add database environment variables.
- Add sample seed data.
- Document local setup.

Done when:

- A new developer can run the project from documentation.
- Backend and database start consistently.

### Stage 12: Testing

Goal: Add confidence without overbuilding.

Backend tests:

- Auth service tests.
- Property service tests.
- Tenant service tests.
- Lease validation tests.
- Payment status tests.
- Controller integration tests for major APIs.

Frontend tests:

- Auth guard test.
- Service test for API calls.
- Basic component tests for important forms.

Done when:

- Core business logic has test coverage.
- Tests run from command line.

### Stage 13: Deployment

Goal: Make the project live and portfolio-ready.

Tasks:

- Deploy database to AWS RDS or another managed database.
- Deploy backend to AWS EC2, Render, Railway, or similar.
- Deploy frontend to Netlify, Vercel, S3, or Nginx.
- Configure environment variables.
- Configure CORS for production frontend URL.
- Add production profile.

Done when:

- Live frontend can talk to live backend.
- Backend uses production database.
- Demo user accounts are available.

### Stage 14: Portfolio Polish

Goal: Make it impressive to recruiters and interviewers.

Tasks:

- Write strong README.
- Add screenshots.
- Add architecture diagram.
- Add database ER diagram.
- Add API documentation link.
- Add demo credentials.
- Record short demo video.
- Add resume bullet points.

Done when:

- A recruiter can understand the project in 2 minutes.
- An interviewer can inspect the technical depth.

## 11. MVP Feature Checklist

Must have:

- User registration and login
- JWT authentication
- Role-based authorization
- Property CRUD
- Tenant CRUD
- Lease creation and activation
- Rent payment tracking
- Maintenance requests
- Dashboard summary
- React frontend
- Docker Compose database
- Swagger documentation
- README

Should have:

- Pagination
- Search and filtering
- Global error handling
- Input validation
- Seed data
- Basic tests
- Deployment

Could have later:

- Redis caching
- Email reminders
- Payment gateway integration
- File upload to cloud storage
- PDF reports
- MongoDB audit logs
- WebSocket notifications
- Map view
- E-signature integration

## 12. Security Requirements

- Passwords must be hashed with BCrypt.
- Plain text passwords must never be stored.
- JWT secret must come from environment variables.
- Protected APIs must require authentication.
- Role-based APIs must verify user permissions.
- Property managers must not access other managers' data.
- Tenants must not access other tenants' data.
- Validation errors must not expose stack traces.
- Production CORS must allow only the real frontend URL.

## 13. API Error Format

Use a consistent error format:

```json
{
  "timestamp": "2026-08-31T12:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Monthly rent must be greater than zero",
  "path": "/api/leases"
}
```

## 14. Pagination Format

Use a standard response for list APIs:

```json
{
  "content": [],
  "page": 0,
  "size": 10,
  "totalElements": 52,
  "totalPages": 6,
  "last": false
}
```

## 15. Seed Data

Create seed data for demo:

- One admin
- Two property managers
- Five properties
- Ten tenants
- Several active leases
- Paid and overdue rent payments
- Open and resolved maintenance requests

Suggested demo accounts:

```text
admin@rentalops.dev / password123
manager@rentalops.dev / password123
tenant@rentalops.dev / password123
```

Never use these passwords in production.

## 16. Suggested Resume Bullets

Use bullets like these after the project is built:

- Built RentalOps, a full-stack property management SaaS using Spring Boot, React, TypeScript, JWT, JPA, and PostgreSQL.
- Designed normalized relational schema for properties, tenants, leases, rent payments, and maintenance workflows.
- Implemented role-based access control for Admin, Property Manager, and Tenant users.
- Developed dashboard analytics for occupancy, rent collection, overdue payments, and maintenance tracking.
- Containerized the application with Docker and deployed backend/frontend with a managed SQL database.

## 17. Agent Implementation Rules

Agents building this project should follow these rules:

- Build incrementally by stage.
- Do not add advanced features before MVP workflows work.
- Keep backend and frontend separated.
- Prefer clear service-layer business logic over placing logic in controllers.
- Use DTOs instead of exposing entities directly from controllers.
- Add validation annotations to request DTOs.
- Use migrations if Flyway or Liquibase is added.
- Keep commits small and meaningful.
- Update README whenever setup or behavior changes.
- Keep security checks server-side even if frontend hides UI controls.
- Make the project runnable locally before optimizing deployment.
