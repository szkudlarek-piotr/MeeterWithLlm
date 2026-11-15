"""Read-only training input; no export file, credentials or records in logs."""
import os
from urllib.parse import parse_qs, urlsplit


def load_dataset(account_id=None):
    import pymysql
    url = urlsplit(os.environ["DB_URL"].removeprefix("jdbc:"))
    if url.scheme not in {"mysql", "mariadb"} or not url.hostname or not url.path.strip("/"):
        raise ValueError("DB_URL musi wskazywać bazę MySQL/MariaDB")
    if url.username or url.password or set(parse_qs(url.query)) - {"useUnicode", "characterEncoding", "serverTimezone"}:
        raise ValueError("Użyj DB_USER/DB_PASSWORD i obsługiwanych opcji JDBC")
    connection = None
    try:
        connection = pymysql.connect(host=url.hostname, port=url.port or 3306, database=url.path.lstrip("/"),
            user=os.environ["DB_USER"], password=os.environ["DB_PASSWORD"], charset="utf8mb4",
            cursorclass=pymysql.cursors.DictCursor, connect_timeout=10, autocommit=False)
        with connection.cursor() as cur:
            cur.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ")
            cur.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT, READ ONLY")
            clause = " AND i.created_by_id=%s" if account_id is not None else ""
            parameters = (account_id,) if account_id is not None else ()
            cur.execute("""
                SELECT i.id, i.created_by_id AS account_id, u.human_id AS self_id,
                       i.name, i.created_at, i.start_date,
                       COALESCE(m.place_string,e.place_string,'') AS place_text
                FROM interactions i JOIN app_users u ON u.id=i.created_by_id
                LEFT JOIN meetings m ON m.id=i.id LEFT JOIN events e ON e.id=i.id
                WHERE 1=1
            """ + clause + " ORDER BY i.start_date,i.id", parameters)
            rows = {r["id"]: {**r, "created_at": str(r["created_at"]), "start_date": str(r["start_date"]),
                              "participants": [], "places": []} for r in cur.fetchall()}
            cur.execute("""
                SELECT ih.interaction_id,ih.human_id FROM interaction_human ih
                JOIN interactions i ON i.id=ih.interaction_id JOIN app_users u ON u.id=i.created_by_id
                WHERE ih.human_id<>u.human_id
            """ + clause, parameters)
            for link in cur.fetchall():
                rows[link["interaction_id"]]["participants"].append(link["human_id"])
            cur.execute("""
                SELECT DISTINCT x.interaction_id,p.id,p.name FROM (
                    SELECT id AS interaction_id,place_id FROM interactions
                    UNION SELECT trip_id,place_id FROM trip_place
                    UNION SELECT id,ceremony_place_id FROM weddings
                    UNION SELECT id,party_place_id FROM weddings
                    UNION SELECT id,hotel_id FROM weddings
                ) x JOIN places p ON p.id=x.place_id
                JOIN interactions i ON i.id=x.interaction_id JOIN app_users u ON u.id=i.created_by_id
                WHERE 1=1
            """ + clause + " ORDER BY x.interaction_id,p.id", parameters)
            for place in cur.fetchall():
                rows[place["interaction_id"]]["places"].append({"id": place["id"], "name": place["name"]})
            return list(rows.values())
    except (pymysql.MySQLError, OSError):
        raise RuntimeError("Odczyt bazy nie powiódł się; szczegóły połączenia ukryto") from None
    finally:
        if connection is not None:
            connection.rollback()
            connection.close()
