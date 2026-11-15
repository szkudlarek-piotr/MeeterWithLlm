-- Flyway V3: shared product news. Uses the connected database, never a fixed schema.
-- MariaDB. Run after a database backup with the backend stopped.
CREATE TABLE IF NOT EXISTS app_news (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    slug VARCHAR(100) NOT NULL UNIQUE,
    title VARCHAR(160) NOT NULL,
    content VARCHAR(10240) NOT NULL,
    published_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


INSERT INTO app_news (slug, title, content, published_at)
SELECT '2026-10-22-ludzie-kalendarz-cytaty', 'Ludzie, wspólne chwile i cytaty',
CONCAT('Kafelki ludzi pokazują liczbę wspólnych wizyt oraz losowy publiczny cytat. Lista pomija Twoją własną osobę.', CHAR(10), CHAR(10),
       'W kalendarzu możesz przeglądać wspólne chwile i szczegóły dnia, a także dodawać i usuwać uczestników zgodnie ze swoimi uprawnieniami. Portrety zaimportowanych uczestników korzystają z dostępu dostępnego na liście ludzi.', CHAR(10), CHAR(10),
       'W grze „Zgadnij autora” rozpoznajesz autorów publicznych cytatów. Możesz wrócić do rozpoczętej gry i sprawdzić historię oraz ranking.', CHAR(10), CHAR(10),
       'Cytaty znajdziesz w kafelkach ludzi i grze. Nadal możesz dodawać nowe cytaty, a osobna kolekcja zniknęła z menu.', CHAR(10), CHAR(10),
       'Aktualności informują teraz o nowych funkcjach i usprawnieniach Meetera.'),
       '2026-10-22 00:00:00'
WHERE NOT EXISTS (SELECT 1 FROM app_news WHERE slug='2026-10-22-ludzie-kalendarz-cytaty');

