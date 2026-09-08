-- A tenant pays rent outside the app (UPI, bank transfer, cash) and then reports it here.
-- The charge is not marked PAID by this — it carries a "reported" marker until the property
-- manager confirms (mark-paid) or dismisses it. These columns hold the tenant's claim.

alter table rent_payments add column reported_paid_at   timestamp(6) with time zone;
alter table rent_payments add column reported_method     varchar(255);
alter table rent_payments add column reported_reference  varchar(255);
alter table rent_payments add column reported_note       varchar(500);
