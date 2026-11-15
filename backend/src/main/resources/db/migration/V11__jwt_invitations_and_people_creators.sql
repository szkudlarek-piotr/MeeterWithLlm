alter table people add column if not exists created_by_id bigint;

delete from pending_registrations;
alter table pending_registrations drop column username;
alter table pending_registrations drop column email;
