-- Keep all existing primary/foreign keys and relationship metadata intact.
-- UUID() is evaluated per row; existing UUIDs on other entities are unchanged.
alter table places add column uuid uuid;
update places set uuid = UUID() where uuid is null;
alter table places modify column uuid uuid not null default UUID();
alter table places add constraint uk_place_public_uuid unique (uuid);

alter table cliques add column uuid uuid;
update cliques set uuid = UUID() where uuid is null;
alter table cliques modify column uuid uuid not null default UUID();
alter table cliques add constraint uk_clique_public_uuid unique (uuid);

alter table quote_game_rounds add column uuid uuid;
update quote_game_rounds set uuid = UUID() where uuid is null;
alter table quote_game_rounds modify column uuid uuid not null default UUID();
alter table quote_game_rounds add constraint uk_game_round_public_uuid unique (uuid);

alter table app_news add column uuid uuid;
update app_news set uuid = UUID() where uuid is null;
alter table app_news modify column uuid uuid not null default UUID();
alter table app_news add constraint uk_news_public_uuid unique (uuid);
