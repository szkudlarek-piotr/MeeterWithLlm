# Aktualności Meetera

Widok „Aktualności” pokazuje zmiany produktu pobierane z bazy, nie historię prywatnych interakcji. `src/NewsPanel.jsx` korzysta z `src/api.js` i `GET /api/news?page=0&size=10`. Obsługuje ładowanie, błąd z ponowieniem, pustą listę i strony. Interakcje pozostają dostępne w kalendarzu i dotychczasowym API.

`app_news` zawiera `id`, unikalny `slug` (stały klucz wpisu, niewidoczny w UI), `title` (160), `content` (10240) oraz lokalny `published_at`. Wpisy są wspólne dla wszystkich zalogowanych kont. Endpoint jest tylko do odczytu; nie ma API edycji ani usuwania. Zwraca DTO `{content: [{id, title, content, publishedAt}], page, size, totalElements, hasNext}`. Parametry: page 0–1000000, size 1–100. Sortowanie: data publikacji i ID malejąco. Przyszłe publikacje są pomijane, według czasu Europe/Warsaw. Treść jest zwykłym tekstem renderowanym przez React, z zachowaniem podziałów wierszy.

Migracja Flyway `V3__product_news.sql` tworzy tabelę i dodaje pierwszy wpis tylko, gdy nie istnieje jego slug. Korzysta z aktualnie podłączonej bazy, bez zapisanej na sztywno nazwy schematu. Do przeniesienia tej aktualności wystarczy przekopiować kod i uruchomić migracje zgodnie z `MIGRATIONS.md`; nie trzeba kopiować danych całej bazy. Kolejne wpisy dodawaj w nowych migracjach. Aplikacja nie generuje wiadomości przy każdym starcie.
