create table auth_events (
    id bigint not null auto_increment primary key,
    occurred_at datetime(6) not null,
    account_id bigint,
    session_record_id bigint,
    username varchar(64) not null,
    event_type varchar(32) not null,
    ip_address varchar(64) not null,
    user_agent varchar(512)
);
create index idx_auth_event_time on auth_events (occurred_at, id);
create index idx_auth_event_account_time on auth_events (account_id, occurred_at, id);
