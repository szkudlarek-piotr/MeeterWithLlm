create table pending_registrations (
    token_hash varchar(64) not null primary key,
    username varchar(64) not null,
    email varchar(254) not null,
    first_name varchar(100) not null,
    surname varchar(100) not null,
    expires_at datetime(6) not null
);
create index idx_pending_registration_expiry on pending_registrations (expires_at);
alter table app_users add column email varchar(254) unique;
