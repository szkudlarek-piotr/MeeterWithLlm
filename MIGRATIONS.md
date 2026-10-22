# Migracje Flyway (MariaDB 10.7+)

Schemat zmienia Flyway 12.4.0, w wersji zarządzanej przez Spring Boot 4.1.1. Hibernate używa `ddl-auto=validate`: sprawdza mapowania, ale nie tworzy ani nie aktualizuje tabel. Migracje z `src/main/resources/db/migration` wykonują się przed inicjalizacją JPA podczas startu backendu. Numery, sumy kontrolne i wyniki są zapisane w `flyway_schema_history`. `clean` jest wyłączone.

Przed pierwszym uruchomieniem na istniejącej bazie zatrzymaj backend i zapisz pełny backup bazy oraz zachowaj pliki zdjęć. Sprawdź, czy DB_URL wskazuje nową bazę Meeter, nigdy oryginalne `real_meeter`. Nie kopiuj historii Flyway między bazami ręcznie.

- `V1__initial_schema.sql`: pełny początkowy schemat dla pustej bazy. Nie zawiera danych osobowych, haseł ani identyfikatorów pochodzących z sandboxa; został wygenerowany z mapowań encji.
- Istniejąca niepusta baza bez historii otrzymuje automatycznie baseline 1. Flyway pomija V1 i zachowuje rekordy. To zakłada dotychczasowy schemat tej aplikacji, nie dowolną starą strukturę. [Semantyka baseline w dokumentacji Flyway](https://documentation.red-gate.com/flyway/reference/commands/baseline).
- `V2__align_existing_schema.sql`: uzupełnia starsze kopie o tabelę metadanych zdjęć, powiązanie portretu i flagę zaproszenia na wesele. Nie nadpisuje istniejących flag; przy braku flagi wartością jest false, bez domyślania zaproszenia. Zachowuje relacje i dane.
- `V3__product_news.sql`: tabela aktualności i pierwszy wpis; stały slug chroni przed duplikatem także po wcześniejszym ręcznym imporcie wpisu.

Uruchomienie samych migracji (bez serwera i inicjalizatorów danych), z katalogu głównego:

```powershell
. .\set_agent_sandbo_env.ps1
$env:MAVEN_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
Push-Location backend
try {
    .\mvnw.cmd flyway:info
    .\mvnw.cmd flyway:migrate
    .\mvnw.cmd flyway:validate
} finally { Pop-Location }
```

Backend może także wykonać migracje przy zwykłym starcie po ustawieniu DB_URL, DB_USER i DB_PASSWORD w tym samym PowerShell. W swoim środowisku wskaż swoją bazę. Przenosisz pliki aplikacji i nowe migracje; istniejących danych nie musisz eksportować tylko po to, aby zastosować zmianę schematu lub aktualność produktu.

Nowe zmiany dopisuj w `V4__opis.sql`, `V5__opis.sql` itd. Nigdy nie edytuj już zastosowanych migracji ani ich sum kontrolnych. Nie używaj `repair` do ukrywania różnic. Nie dodawaj `USE`, nazw sandboxa, danych kont ani credentiali. Importy prywatnych wizyt/spotkań/eventów z katalogu głównego pozostają osobnymi operacjami, nie uruchamiają się przez Flyway.

MariaDB DDL może zatwierdzać zmiany poza transakcją. Po błędzie sprawdź stan tabel i historię, zanim ponowisz operację; pełny backup jest podstawą odtworzenia. Samo `CREATE TABLE IF NOT EXISTS` nie potwierdza zgodności istniejącej tabeli — robi to dodatkowo walidacja JPA przy starcie.

Testy aplikacji nadal używają odizolowanego H2 z `create-drop` i wyłączonym Flyway. `FlywayMigrationTests` sprawdza tworzenie pustej bazy oraz ponowienie V1/V3 na H2 w trybie MariaDB; MariaDB-specyficzne V2 wymaga sprawdzenia na sandboxie. `SchemaExportTests` zapisuje wyłącznie referencyjny schemat do `target/current-mariadb-schema.sql`, bez połączenia z danymi użytkownika.
