# Migracje Flyway (MariaDB 10.7+)

## Backup przed aktualizacją

Po zatrzymaniu backendu uruchom z katalogu głównego `.\backup.ps1`. Skrypt tworzy w systemowym folderze Dokumenty katalog `backup_DD_MM_YYYY_HH_MM`. Korzysta wyłącznie ze zmiennych `DB_URL`, `DB_USER`, `DB_PASSWORD` odziedziczonych z bieżącej sesji PowerShell (tych samych, z którymi uruchamiasz backend), bez ładowania skryptu sandboxa. Przy brakującej konfiguracji przerywa pracę. Eksportuje wybraną w środowisku bazę MariaDB/MySQL do `database.sql` przez `mariadb-dump.exe` lub `mysqldump.exe`, następnie kopiuje portrety, zdjęcia wydarzeń, archiwum portretów, zdjęcia klik i ikony kategorii. `manifest.json` zawiera rozmiary i SHA-256; brakujące foldery są jawnie odnotowane. Nie kopiuje skryptu z hasłami. Sam SQL zawiera dane osobowe i hashe kont, dlatego kopia wymaga prywatnego przechowywania.

Skrypt znajduje klienta dump w PATH lub typowych katalogach instalacji. Własną lokalizację podaj jako `.\backup.ps1 -DumpExecutable 'C:\Program Files\MariaDB 11.4\bin\mariadb-dump.exe'`. Hasło jest przekazywane przez tymczasowy plik opcji z ACL ograniczonym do bieżącego użytkownika, usuwany w `finally`, a nie w argumentach procesu. Wymagane są uprawnienia do eksportu tabel, procedur, zdarzeń i wyzwalaczy. JDBC URL z dodatkowymi opcjami jest odrzucany, aby nie pomijać np. wymagań TLS.

Opcjonalne `-DestinationRoot`, `-BackendPort` i `-PhotoDirectories @{ people='...'; events='...'; former='...'; cliques='...'; categories='...' }` pozwalają wskazać inne ścieżki; względne ścieżki zdjęć są liczone od `backend`. Domyślne ścieżki uwzględniają `application.properties` i odpowiadające mu zmienne Spring `APP_PHOTOS_*`. Przy ścieżkach przekazanych przy starcie przez argumenty JVM/Spring albo dodatkowy profil podaj te same ścieżki przez `-PhotoDirectories`.

Port backendu jest odczytywany ze zmiennej `SERVER_PORT` (domyślnie 8091) i może być nadpisany przez `-BackendPort`. Backend musi pozostać zatrzymany przez całą operację; skrypt sprawdza nasłuch na początku i końcu, ale nie zatrzymuje procesów. Wstrzymaj też inne źródła zapisu i zmiany DDL. Eksport używa `--single-transaction` dla InnoDB. Istniejący folder z tej samej minuty nie jest nadpisywany. Błąd pozostawia `INCOMPLETE.txt`; tylko kopia bez tego znacznika jest ukończona. Skrypt nie wykonuje migracji ani odtwarzania i nie uruchamia się automatycznie.

Ręczny import prywatnych podróży z oryginalnej bazy opisuje [TRIP_IMPORT.md](TRIP_IMPORT.md); skrypt `../migrate_trips.sql` pozostaje poza Flyway.

Schemat zmienia Flyway 12.4.0, w wersji zarządzanej przez Spring Boot 4.1.1. Hibernate używa `ddl-auto=validate`: sprawdza mapowania, ale nie tworzy ani nie aktualizuje tabel. Migracje z `src/main/resources/db/migration` wykonują się przed inicjalizacją JPA podczas startu backendu. Numery, sumy kontrolne i wyniki są zapisane w `flyway_schema_history`. `clean` jest wyłączone.

Przed pierwszym uruchomieniem na istniejącej bazie zatrzymaj backend i zapisz pełny backup bazy oraz zachowaj pliki zdjęć. Sprawdź, czy DB_URL wskazuje nową bazę Meeter, nigdy oryginalne `real_meeter`. Nie kopiuj historii Flyway między bazami ręcznie.

- `V1__initial_schema.sql`: pełny początkowy schemat dla pustej bazy. Nie zawiera danych osobowych, haseł ani identyfikatorów pochodzących z sandboxa; został wygenerowany z mapowań encji.
- Istniejąca niepusta baza bez historii otrzymuje automatycznie baseline 1. Flyway pomija V1 i zachowuje rekordy. To zakłada dotychczasowy schemat tej aplikacji, nie dowolną starą strukturę. [Semantyka baseline w dokumentacji Flyway](https://documentation.red-gate.com/flyway/reference/commands/baseline).
- `V2__align_existing_schema.sql`: uzupełnia starsze kopie o tabelę metadanych zdjęć, powiązanie portretu i flagę zaproszenia na wesele. Nie nadpisuje istniejących flag; przy braku flagi wartością jest false, bez domyślania zaproszenia. Zachowuje relacje i dane.
- `V3__product_news.sql`: tabela aktualności i pierwszy wpis; stały slug chroni przed duplikatem także po wcześniejszym ręcznym imporcie wpisu.
- `V4__clique_photos.sql`: typ zdjęcia CLIQUE i `cliques.photo_id`. Import zdjęć z folderu uruchamia wyłącznie administrator w panelu; szczegóły w [CLIQUES.md](CLIQUES.md).
- `V6__former_human_photos.sql`: rozszerza enum `photos.photo_type` o `FORMER_HUMAN_PHOTO`. Nie przenosi ani nie modyfikuje istniejących zdjęć; archiwizacja następuje dopiero przy jawnej podmianie portretu. Przed startem nowej wersji zatrzymaj backend i wykonaj backup bazy oraz folderów zdjęć.
- `V7__auth_event_history.sql`: trwały rejestr logowań, błędnych prób, blokad limitera, wylogowań i zmian hasła. Bez haseł/tokenów i bez kaskadowego usuwania razem z kontem lub sesją. Szczegóły w AUTH_REVIEW.md; historycznych logowań nie uzupełnia.
- `V8__public_entity_uuids.sql`: adds and backfills persistent, unique, non-null UUID columns on places, cliques, game rounds and news. Existing primary/foreign keys and audit metadata are preserved. Browser requests and image URLs use the UUID `/api/v2` contract; see UUID_API.md. Back up and stop the backend before adoption.

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

Nowe zmiany dopisuj w `V5__opis.sql`, `V6__opis.sql` itd. Nigdy nie edytuj już zastosowanych migracji ani ich sum kontrolnych. Nie używaj `repair` do ukrywania różnic. Nie dodawaj `USE`, nazw sandboxa, danych kont ani credentiali. Importy prywatnych wizyt/spotkań/eventów z katalogu głównego pozostają osobnymi operacjami, nie uruchamiają się przez Flyway.

MariaDB DDL może zatwierdzać zmiany poza transakcją. Po błędzie sprawdź stan tabel i historię, zanim ponowisz operację; pełny backup jest podstawą odtworzenia. Samo `CREATE TABLE IF NOT EXISTS` nie potwierdza zgodności istniejącej tabeli — robi to dodatkowo walidacja JPA przy starcie.

Testy aplikacji nadal używają odizolowanego H2 z `create-drop` i wyłączonym Flyway. `FlywayMigrationTests` sprawdza tworzenie pustej bazy oraz ponowienie V1/V3/V4 na H2 w trybie MariaDB; MariaDB-specyficzne V2 wymaga sprawdzenia na sandboxie. `SchemaExportTests` zapisuje wyłącznie referencyjny schemat do `target/current-mariadb-schema.sql`, bez połączenia z danymi użytkownika.
