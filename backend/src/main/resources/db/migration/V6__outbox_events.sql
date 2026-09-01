-- Outbox pattern: domain events are recorded here in the *same* transaction as the business
-- change that caused them (see NotificationEventListener), instead of triggering side effects
-- (e.g. writing a Notification row) directly inside that transaction. A separate worker
-- (OutboxProcessor) drains PENDING rows on its own schedule, so delivery is eventually
-- consistent and survives a crash between "the fact was committed" and "the notification was
-- sent" — there is no window where the two can silently diverge.

create table outbox_events (
    id           uuid primary key,
    created_at   timestamp(6) with time zone not null,
    updated_at   timestamp(6) with time zone not null,
    event_type   varchar(255) not null,
    payload      varchar(4000) not null,
    status       varchar(255) not null,
    processed_at timestamp(6) with time zone,
    retry_count  integer not null default 0,
    last_error   varchar(1000),
    constraint ck_outbox_events_status check (status in ('PENDING', 'PROCESSED', 'FAILED'))
);

-- The processor's hot path is "give me the oldest PENDING rows"; this index serves it directly.
create index ix_outbox_events_status_created on outbox_events (status, created_at);
