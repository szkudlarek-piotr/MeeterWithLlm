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
        String url="jdbc:h2:mem:flyway-"+UUID.randomUUID()+";MODE=MariaDB;DB_CLOSE_DELAY=-1";
        var flyway=Flyway.configure().dataSource(url,"sa","").locations("filesystem:"+location).cleanDisabled(true).load();
        assertEquals(2,flyway.migrate().migrationsExecuted);
        try(var connection=DriverManager.getConnection(url,"sa","");var statement=connection.createStatement()) {
            var rows=statement.executeQuery("select count(*) from app_news");
            assertTrue(rows.next()); assertEquals(1,rows.getInt(1));
            statement.executeUpdate("insert into places (name, latitude, longitude) values ('Preserved', 1, 2)");
        }
        assertEquals(0,flyway.migrate().migrationsExecuted);
        flyway.validate();
        try(var connection=DriverManager.getConnection(url,"sa","");var statement=connection.createStatement()) {
            var rows=statement.executeQuery("select count(*) from places where name='Preserved'");
            assertTrue(rows.next()); assertEquals(1,rows.getInt(1));
        }
    }
}
