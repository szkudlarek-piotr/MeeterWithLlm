package org.example.meeter.graph;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.concurrent.TimeUnit;
import java.util.*;
import org.neo4j.driver.*;

/** Standalone, read-only SQL export. Does not start Spring, Flyway or account bootstrap. */
public final class GraphSync {
    private static final int BATCH_SIZE = 500;
    record Snapshot(List<Map<String, Object>> people, List<Map<String, Object>> edges) {}

    public static void main(String[] args) throws Exception {
        long interval = args.length == 0 ? 0 : Long.parseLong(args[0]);
        if (interval < 0 || args.length > 1) throw new IllegalArgumentException("Expected optional interval in seconds >= 0");
        String sqlUrl = required("DB_URL");
        String sqlUser = required("DB_USER");
        String sqlPassword = required("DB_PASSWORD");
        String source = System.getenv().getOrDefault("GRAPH_SOURCE", "meeter");
        if (source.isBlank()) throw new IllegalArgumentException("GRAPH_SOURCE cannot be blank");
        try (Driver driver = GraphDatabase.driver(required("NEO4J_URL"),
                AuthTokens.basic(required("NEO4J_USER"), required("NEO4J_PASSWORD")),
                Config.builder().withConnectionTimeout(20, TimeUnit.SECONDS).build())) {
            do {
                try {
                    sync(driver, System.getenv().getOrDefault("NEO4J_DATABASE", "neo4j"), source,
                            sqlUrl, sqlUser, sqlPassword);
                } catch (Exception failure) {
                    // Exception messages/stack traces can include connection URLs or personal data.
                    System.err.println("Graph sync failed (" + failure.getClass().getSimpleName()
                            + "). No partial graph snapshot was committed.");
                    if (interval == 0) throw new IllegalStateException("Graph sync failed; check connectivity, credentials and schema.");
                }
                if (interval > 0) Thread.sleep(Math.multiplyExact(interval, 1000));
            } while (interval > 0);
        }
    }

    static Snapshot readSnapshot(Connection connection) throws SQLException {
        connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        connection.setReadOnly(true);
        connection.setAutoCommit(false);
        try {
            var people = read(connection, """
                    select id, cast(uuid as char(36)) as uuid, name, surname, created_by_id as ownerId
                    from people order by id
                    """);
            var edges = read(connection, """
                    select i.id as interactionId, a.human_id as fromId, b.human_id as toId,
                      i.start_date as startDate, i.end_date as endDate, i.created_by_id as ownerId,
                      case when m.id is not null then 'MEETING' when v.id is not null then 'VISIT'
                        when e.id is not null then 'EVENT' when t.id is not null then 'TRIP'
                        when f.id is not null then 'FLATMATE' end as interactionType
                    from interactions i
                    join interaction_human a on a.interaction_id=i.id
                    join interaction_human b on b.interaction_id=i.id and a.human_id < b.human_id
                    left join meetings m on m.id=i.id left join visits v on v.id=i.id
                    left join events e on e.id=i.id left join trips t on t.id=i.id
                    left join flatmates f on f.id=i.id
                    where not exists (select 1 from weddings w where w.id=i.id)
                      and (m.id is not null or v.id is not null or e.id is not null or t.id is not null or f.id is not null)
                    order by i.id, a.human_id, b.human_id
                    """);
            connection.commit();
            return new Snapshot(people, edges);
        } catch (SQLException failure) {
            connection.rollback();
            throw failure;
        }
    }

    private static List<Map<String, Object>> read(Connection connection, String sql) throws SQLException {
        var rows = new ArrayList<Map<String, Object>>();
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            var metadata = result.getMetaData();
            while (result.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    Object value = result.getObject(i);
                    if (value instanceof Timestamp timestamp) value = timestamp.toLocalDateTime();
                    row.put(metadata.getColumnLabel(i).toLowerCase(Locale.ROOT), value);
                }
                rows.add(row);
            }
        }
        return rows;
    }

    public static void sync(Driver driver, String database, String source,
                            String sqlUrl, String sqlUser, String sqlPassword) {
        try (var session = driver.session(SessionConfig.forDatabase(database))) {
            session.run("CREATE CONSTRAINT meeter_person_key IF NOT EXISTS FOR (p:MeeterPerson) REQUIRE (p.source, p.id) IS UNIQUE").consume();
            session.run("CREATE CONSTRAINT meeter_sync_key IF NOT EXISTS FOR (s:MeeterGraphSync) REQUIRE s.source IS UNIQUE").consume();
            var counts = session.executeWrite(tx -> {
                // Obtain a write lock BEFORE reading SQL, serializing even independent sync processes.
                tx.run("MERGE (s:MeeterGraphSync {source:$source}) SET s.lockVersion=coalesce(s.lockVersion,0)+1",
                        Map.of("source", source)).consume();
                Snapshot snapshot;
                try (var connection = DriverManager.getConnection(sqlUrl, sqlUser, sqlPassword)) {
                    snapshot = readSnapshot(connection);
                } catch (SQLException failure) {
                    throw new IllegalStateException("SQL snapshot unavailable");
                }
                String generation = UUID.randomUUID().toString();
                Map<String, Object> parameters = new HashMap<>(Map.of("source", source, "generation", generation));
                batches(tx, snapshot.people(), parameters, """
                        UNWIND $rows AS row
                        MERGE (p:MeeterPerson {source:$source, id:row.id})
                        SET p.uuid=row.uuid, p.name=row.name, p.surname=row.surname,
                            p.ownerId=row.ownerid, p.generation=$generation
                        """);
                batches(tx, snapshot.edges(), parameters, """
                        UNWIND $rows AS row
                        MATCH (a:MeeterPerson {source:$source, id:row.fromid})
                        MATCH (b:MeeterPerson {source:$source, id:row.toid})
                        MERGE (a)-[r:MEETER_INTERACTION {source:$source, interactionId:row.interactionid}]->(b)
                        SET r.type=row.interactiontype, r.date=date(row.startdate),
                            r.startDate=row.startdate, r.endDate=row.enddate,
                            r.ownerId=row.ownerid, r.generation=$generation
                        """);
                tx.run("MATCH ()-[r:MEETER_INTERACTION {source:$source}]->() WHERE r.generation IS NULL OR r.generation<>$generation DELETE r", parameters).consume();
                // Do not detach-delete: unrelated relationships must never be silently removed.
                tx.run("MATCH (p:MeeterPerson {source:$source}) WHERE p.generation IS NULL OR p.generation<>$generation DELETE p", parameters).consume();
                tx.run("MATCH (s:MeeterGraphSync {source:$source}) SET s.completedAt=datetime(), s.people=$people, s.relationships=$edges",
                        Map.of("source", source, "people", snapshot.people().size(), "edges", snapshot.edges().size())).consume();
                return new int[]{snapshot.people().size(), snapshot.edges().size()};
            });
            System.out.printf("Graph sync complete: people=%d, relationships=%d%n", counts[0], counts[1]);
        }
    }

    private static void batches(TransactionContext tx, List<Map<String, Object>> rows,
                                Map<String, Object> parameters, String query) {
        for (int offset = 0; offset < rows.size(); offset += BATCH_SIZE) {
            parameters.put("rows", rows.subList(offset, Math.min(rows.size(), offset + BATCH_SIZE)));
            tx.run(query, parameters).consume();
        }
        parameters.remove("rows");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing environment variable: " + name);
        return value;
    }
}
