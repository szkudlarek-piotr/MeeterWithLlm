alter table photos modify column photo_type enum ('EVENT','PROFILE_PICTURE','SUPERPOWER','TRIP','CLIQUE') not null;
alter table cliques add column photo_id bigint;
alter table cliques add constraint fk_clique_photo foreign key (photo_id) references photos(id);
