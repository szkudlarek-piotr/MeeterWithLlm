create table account_invitation_history (
    invited_user_id bigint not null primary key,
    inviter_user_id bigint not null,
    invited_human_id bigint not null,
    accepted_at datetime(6) not null
);

create index idx_account_invitation_history_inviter on account_invitation_history (inviter_user_id);
