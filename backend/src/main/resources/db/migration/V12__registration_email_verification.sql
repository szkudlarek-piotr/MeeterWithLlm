alter table pending_registrations add column verification_hash varchar(64);
alter table pending_registrations add column username varchar(64);
alter table pending_registrations add column email varchar(254);
alter table pending_registrations add column password_hash varchar(255);
create unique index idx_pending_registration_verification on pending_registrations (verification_hash);
