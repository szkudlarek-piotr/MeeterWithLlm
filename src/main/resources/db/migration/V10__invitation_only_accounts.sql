delete from pending_registrations;

alter table pending_registrations drop column first_name;
alter table pending_registrations drop column surname;
alter table pending_registrations add column human_id bigint not null;
alter table pending_registrations add column inviter_id bigint not null;
