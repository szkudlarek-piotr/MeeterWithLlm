package org.example.meeter.recommendations;

import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

class ParticipantHistoryTests {
    @Test void historyIsOwnerScopedExcludesSelfAndIncludesAllPlaceKinds() throws Exception {
        var data = new SingleConnectionDataSource(DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID(), "sa", ""), true);
        try {
            var sql = new JdbcTemplate(data);
            sql.execute("create table interactions(id bigint,name varchar,created_by_id bigint,place_id bigint,start_date timestamp)");
            sql.execute("create table meetings(id bigint,place_string varchar)");
            sql.execute("create table events(id bigint,place_string varchar)");
            sql.execute("create table visits(id bigint)");
            sql.execute("create table trips(id bigint)");
            sql.execute("create table interaction_human(interaction_id bigint,human_id bigint)");
            sql.execute("create table trip_place(trip_id bigint,place_id bigint)");
            sql.execute("create table weddings(id bigint,ceremony_place_id bigint,party_place_id bigint,hotel_id bigint)");
            sql.execute("create table places(id bigint,name varchar)");
            sql.update("insert into interactions values(1,'Own',10,7,'2026-01-19 23:45:00'),(2,'Foreign',20,8,'2026-01-20 01:00:00'),(3,'Imported',null,8,'2026-01-21 12:00:00')");
            sql.update("insert into meetings values(1,'Text place')");
            sql.update("insert into interaction_human values(1,100),(1,101),(2,202),(3,303)");
            sql.update("insert into places values(7,'First'),(8,'Second'),(9,'Hotel')");
            sql.update("insert into trip_place values(1,7),(1,8)");
            sql.update("insert into weddings values(1,7,8,9)");
            var source = new ParticipantHistory(new NamedParameterJdbcTemplate(data));
            var rows = source.rows(10L, 100L);
            assertEquals(1, rows.size());
            assertEquals("Own", rows.getFirst().get("name"));
            assertEquals(1L, rows.getFirst().get("id"));
            assertEquals("MEETING", rows.getFirst().get("type"));
            assertEquals("Text place", rows.getFirst().get("place_text"));
            assertEquals("2026-01-19", rows.getFirst().get("start_date"));
            assertEquals(List.of(101L), rows.getFirst().get("participants"));
            assertEquals(List.of(Map.of("id",7L,"name","First"),Map.of("id",8L,"name","Second"),Map.of("id",9L,"name","Hotel")), rows.getFirst().get("places"));
            assertEquals(List.of(202L), source.rows(20L, 200L).getFirst().get("participants"));
            assertEquals(List.of(), source.rows(99L, 999L));
        } finally { data.destroy(); }
    }
}
