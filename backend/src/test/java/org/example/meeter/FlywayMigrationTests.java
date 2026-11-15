package org.example.meeter;

import java.nio.file.*;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlywayMigrationTests {
    @Test void initialSchemaAndNewsRunOnEmptyDatabaseAndValidateOnRepeat() throws Exception {
        // H2 checks bootstrap and seed SQL. MariaDB-only compatibility DDL is
        // checked on the configured sandbox, not disguised as a portable script.
        var location=Files.createTempDirectory("meeter-migrations-");
        var migrations=Path.of("src/main/resources/db/migration");
        Files.copy(migrations.resolve("V1__initial_schema.sql"),location.resolve("V1__initial_schema.sql"));
        Files.copy(migrations.resolve("V3__product_news.sql"),location.resolve("V3__product_news.sql"));
        Files.copy(migrations.resolve("V4__clique_photos.sql"),location.resolve("V4__clique_photos.sql"));
        Files.copy(migrations.resolve("V5__quote_votes.sql"),location.resolve("V5__quote_votes.sql"));
        Files.copy(migrations.resolve("V6__former_human_photos.sql"),location.resolve("V6__former_human_photos.sql"));
        Files.copy(migrations.resolve("V7__auth_event_history.sql"),location.resolve("V7__auth_event_history.sql"));
        Files.copy(migrations.resolve("V8__public_entity_uuids.sql"),location.resolve("V8__public_entity_uuids.sql"));
        Files.copy(migrations.resolve("V9__account_registration.sql"),location.resolve("V9__account_registration.sql"));
        Files.copy(migrations.resolve("V10__invitation_only_accounts.sql"),location.resolve("V10__invitation_only_accounts.sql"));
        Files.copy(migrations.resolve("V11__jwt_invitations_and_people_creators.sql"),location.resolve("V11__jwt_invitations_and_people_creators.sql"));
        Files.copy(migrations.resolve("V12__registration_email_verification.sql"),location.resolve("V12__registration_email_verification.sql"));
        Files.copy(migrations.resolve("V13__account_invitation_history.sql"),location.resolve("V13__account_invitation_history.sql"));
        String url="jdbc:h2:mem:flyway-"+UUID.randomUUID()+";MODE=MariaDB;DB_CLOSE_DELAY=-1";
        // First populate the old schema, then verify V8 preserves existing records.
        var previous=Flyway.configure().dataSource(url,"sa","").locations("filesystem:"+location).target("7").cleanDisabled(true).load();
        assertEquals(6,previous.migrate().migrationsExecuted);
        try(var connection=DriverManager.getConnection(url,"sa","");var statement=connection.createStatement()) {
            var rows=statement.executeQuery("select count(*) from app_news");
            assertTrue(rows.next()); assertEquals(1,rows.getInt(1));
            statement.executeUpdate("insert into places (name, latitude, longitude) values ('Preserved', 1, 2)");
            statement.executeUpdate("insert into places (name, latitude, longitude) values ('Second', 3, 4)");
            statement.executeUpdate("insert into cliques (clique_name) values ('Preserved'), ('Second')");
            statement.executeUpdate("insert into photos (photo_name, photo_type) values ('archived.jpg', 'FORMER_HUMAN_PHOTO')");
            statement.executeUpdate("insert into auth_events (occurred_at, username, event_type, ip_address) values (current_timestamp, 'fixture', 'LOGIN_FAILURE', '127.0.0.1')");
        }
        var flyway=Flyway.configure().dataSource(url,"sa","").locations("filesystem:"+location).cleanDisabled(true).load();
        assertEquals(6,flyway.migrate().migrationsExecuted);
        String oldUuid;
        try(var connection=DriverManager.getConnection(url,"sa","");var statement=connection.createStatement()) {
            for(String table:new String[]{"places","cliques","app_news"}) {
                var rows=statement.executeQuery("select count(*), count(uuid), count(distinct uuid) from "+table);
                assertTrue(rows.next()); assertEquals(rows.getLong(1),rows.getLong(2)); assertEquals(rows.getLong(1),rows.getLong(3));
            }
            var rows=statement.executeQuery("select uuid from places where name='Preserved'");
            assertTrue(rows.next()); oldUuid=rows.getString(1); assertNotNull(UUID.fromString(oldUuid));
        }
        assertEquals(0,flyway.migrate().migrationsExecuted);
        flyway.validate();
        try(var connection=DriverManager.getConnection(url,"sa","");var statement=connection.createStatement()) {
            var rows=statement.executeQuery("select count(*) from places where name='Preserved'");
            assertTrue(rows.next()); assertEquals(1,rows.getInt(1));
            rows=statement.executeQuery("select uuid from places where name='Preserved'");
            assertTrue(rows.next()); assertEquals(oldUuid,rows.getString(1));
            rows=statement.executeQuery("select count(*) from auth_events");
            assertTrue(rows.next()); assertEquals(1,rows.getInt(1));
            // Manual release-note import: execute against this isolated schema only.
            String release=Files.readString(Path.of("../import_news_2026_11_08.sql")).replace("vibecoded_meeter.","PUBLIC.");
            // Execute the INSERT unchanged; MariaDB's transaction/ROW_COUNT diagnostics
            // are client commands, not the behavior this isolated DML test needs.
            String releaseInsert=release.substring(release.indexOf("INSERT INTO"),release.indexOf("SELECT ROW_COUNT()"));
            statement.executeUpdate(releaseInsert);
            rows=statement.executeQuery("select uuid from app_news where slug='2026-11-08-wspolne-interakcje-profil-uczestnicy'");
            assertTrue(rows.next()); String newsUuid=rows.getString(1); assertNotNull(UUID.fromString(newsUuid));
            statement.executeUpdate(releaseInsert);
            rows=statement.executeQuery("select count(*), min(cast(uuid as varchar)) from app_news where slug='2026-11-08-wspolne-interakcje-profil-uczestnicy'");
            assertTrue(rows.next()); assertEquals(1,rows.getInt(1)); assertEquals(newsUuid,rows.getString(2));
        }
    }
}
