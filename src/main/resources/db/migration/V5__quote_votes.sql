create table quote_votes (
    id bigint not null auto_increment primary key,
    quote_id bigint not null,
    voter_id bigint not null,
    vote_value integer not null,
    constraint uk_quote_vote_account unique (quote_id, voter_id),
    constraint ck_quote_vote_value check (vote_value in (-1, 1)),
    constraint fk_quote_vote_quote foreign key (quote_id) references quotes (id) on delete cascade,
    constraint fk_quote_vote_account foreign key (voter_id) references app_users (id) on delete cascade
);
