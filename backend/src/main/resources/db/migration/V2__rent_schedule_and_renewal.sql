-- Automatic rent billing: each active lease carries a billing day and a pointer to the
-- due date of the next charge to be generated. Renewal links a lease to its predecessor.

alter table leases add column billing_day_of_month integer;
alter table leases add column next_charge_date date;
alter table leases add column renewed_from_lease_id bigint;

update leases
set billing_day_of_month = least(cast(extract(day from start_date) as integer), 28);

-- Existing active leases: start billing from next month so we don't backfill history.
update leases
set next_charge_date = (date_trunc('month', current_date) + interval '1 month'
                        + (least(billing_day_of_month, 28) - 1) * interval '1 day')::date
where lease_status = 'ACTIVE';

alter table leases alter column billing_day_of_month set not null;
