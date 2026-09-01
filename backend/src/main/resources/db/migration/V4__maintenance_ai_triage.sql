-- AI-assisted triage: each maintenance request is classified on creation.
alter table maintenance_requests add column ai_triaged boolean not null default false;
alter table maintenance_requests add column ai_source varchar(255);
alter table maintenance_requests add column ai_category varchar(255);
alter table maintenance_requests add column ai_suggested_priority varchar(255);
alter table maintenance_requests add column ai_summary varchar(500);
alter table maintenance_requests add column ai_cost_band varchar(255);
alter table maintenance_requests add column ai_draft_reply varchar(2000);
