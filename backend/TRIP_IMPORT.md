# Import podróży z oryginalnej bazy

`../migrate_trips.sql` jest osobnym, ręcznym importem `real_meeter` → `vibecoded_meeter`, nie migracją Flyway. Nie dodawaj go do `db/migration`. Źródło jest tylko odczytywane. Skrypt zakłada MariaDB 10.7+ i tabele InnoDB.

## Przed uruchomieniem

1. Zatrzymaj backend i inne procesy zapisujące do obu baz.
2. Wykonaj pełny eksport obu baz w phpMyAdmin (format SQL, struktura i dane). Zachowaj istniejące pliki zdjęć.
3. Potwierdź, że identyfikatory ludzi i miejsc w obu bazach wskazują te same rekordy, jak przy poprzednich importach. Samo istnienie ID nie potwierdza tożsamości.
4. Konto `pszkudlarek` musi istnieć dokładnie raz w bazie docelowej i mieć powiązaną osobę.

## Uruchomienie w phpMyAdmin

Wybierz **vibecoded_meeter** → **Import** → wskaż plik `migrate_trips.sql` z katalogu głównego projektu → wykonaj import całego pliku. Można też wkleić całą zawartość do SQL; klient musi obsługiwać `DELIMITER` i procedury. Nie wykonuj pojedynczych INSERT-ów osobno. Wymagane są SELECT na źródle oraz uprawnienia zapisu, CREATE TABLE i CREATE ROUTINE/ALTER ROUTINE/EXECUTE na celu.

Skrypt tworzy dziennik i procedurę poza transakcją. Dane podróży, powiązania i wpisy dziennika są zapisywane w jednej transakcji. Błąd powoduje ROLLBACK danych; pusta tabela dziennika i procedura mogą pozostać. Nie usuwaj dziennika importu. Nie używaj opcji klienta kontynuującej po błędach. Błąd należy wyjaśnić przed ponownym uruchomieniem.

## Mapowanie

| Źródło | Cel |
| --- | --- |
| `citybreaks.ID` | `migration_real_meeter_trips.source_trip_id`; nowe ID interakcji powstaje niezależnie |
| `Place` | nazwa po TRIM; pusta nazwa → `Podróż #ID`; oryginalny tekst dodatkowo w dzienniku |
| `Date_start`, `Date_stop` | `interactions.start_date`, `end_date`, bez konwersji strefy |
| `long_description` | `interactions.long_desc` bez zmiany treści |
| `category` | zachowana w dzienniku; obecny typ aplikacji to TRIP |
| `start_place_id` | `interactions.place_id` i miejsce w `trip_place` |
| `citybreak_companion` | `interaction_human`, dodatkowo osoba powiązana z kontem importu |
| `trip_place` | docelowe `trip_place`; duplikaty i powtórzone miejsce startowe są scalane |

Nowe relacje otrzymują własne ID, czas importu UTC i twórcę `app_users.id`. Czas ten jest metadanymi migracji, a nie odzyskanym czasem historycznym. Mapy korzystają z istniejących miejsc i ich współrzędnych. Pola tekstowego Place nie geokodujemy ani nie dzielimy na nowe miejsca. Nie importujemy kosztów (`trip_expenses`, `trip_expenses_summary`); model docelowy nie ma tej funkcji. Relacje nie zachowują kolejności miejsc, bo aplikacja przechowuje zbiór.

Walidacja przerywa import przy nieprawidłowych/odwróconych datach, nazwie dłuższej niż 128 znaków, zbyt długim opisie, brakującym człowieku/miejscu, osieroconych powiązaniach lub uszkodzonym dzienniku. Nie obcina tekstów, nie wymyśla dat i nie usuwa źródłowych rekordów.

## Wynik i ponowienie

Sprawdź `imported_trips`, `imported_participant_links`, `imported_place_links`. Końcowe `missing_trips` musi być 0. Ostatnia tabela pokazuje mapowanie starego ID na nowe ID interakcji.

Ponowne wykonanie pomija już zaimportowane ID: liczniki `imported_*` powinny być 0. Zachowuje późniejsze zmiany nazw i powiązań w aplikacji, ich ID oraz metadane. Nie synchronizuje późniejszych zmian źródła. Wspólny lock uniemożliwia dwa równoczesne importy podróży; nie zastępuje zatrzymania innych procesów zapisujących do bazy.

Po sukcesie uruchom backend i sprawdź zakładkę Podróże oraz mapy w profilu uczestnika. Nie są wymagane zmiany encji ani migracja Flyway.

## Test syntetyczny

`backend/Test-TripImport.ps1` tworzy dwie unikalne bazy testowe, używając połączenia z `set_agent_sandbo_env.ps1`. Korzysta wyłącznie ze sztucznych rekordów, a w finally usuwa tylko utworzone przez siebie bazy. Wymaga CREATE/DROP DATABASE i klienta MariaDB. Sprawdza pierwszy import, powtórzenie, zachowanie późniejszych edycji, duplikaty, daty lokalne, brakujące powiązania, tytuł, dopisanie nowych rekordów i uszkodzony dziennik. Nie uruchamia aplikacji.

```powershell
# Z katalogu głównego projektu, na serwerze do testów:
.\backend\Test-TripImport.ps1
```

