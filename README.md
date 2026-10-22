# Aktualna konfiguracja

Ta kopia używa MariaDB i Flyway; instrukcje H2 poniżej są historyczne. Zmiany schematu i aktualności produktu przenosimy wersjonowanymi migracjami — zobacz [MIGRATIONS.md](MIGRATIONS.md) oraz [NEWS.md](NEWS.md). Aktualności pokazują zmiany funkcji, interakcje są w kalendarzu. Kolekcja Cytaty została usunięta; pozostają formularz, kafelki ludzi i gra.

# Backend Meetera

## Baza w tej kopii i konta testowe

Ta kopia korzysta z MariaDB/MySQL wskazanej przez `spring.datasource.*` w `application.properties`, z driverem MariaDB w `pom.xml`. Poniższe starsze opisy H2 dotyczą poprzedniej konfiguracji; testy nadal powinny używać osobnej bazy w pamięci. Nie zapisuj danych dostępowych do bazy w dokumentacji.

Automatyczne tworzenie przykładowych osób, miejsc, klik, interakcji i kont testowych zostało usunięte. Wcześniej zapisane rekordy pozostają w bazie; zmiana kodu ich nie kasuje.

Cytaty, ich API i formularz: [QUOTES.md](QUOTES.md).

Gra w autorów cytatów, timer i ranking: [QUOTE_GAME.md](QUOTE_GAME.md).

Tabele powiązań z własnym ID, datą i autorem oraz migracja starych danych: [RELATIONSHIPS.md](RELATIONSHIPS.md).

Modele i API sześciu typów interakcji oraz uzasadnienie zmian: [INTERACTIONS.md](INTERACTIONS.md).

Import profilówek z linku i opis przykładowych danych: [PHOTOS.md](PHOTOS.md).

Spring Boot 4.1.1, Java 25, Spring Data JPA i osobna baza H2. Projekt nie zawiera połączenia z dotychczasową bazą Meetera ani jej danych.

## Uruchomienie

W katalogu `backend`:

```powershell
.\mvnw.cmd spring-boot:run
```

Maven Wrapper pobierze Maven i zależności przy pierwszym uruchomieniu. Jeśli Java nie ufa certyfikatowi lokalnej sieci, użyj zaufanych certyfikatów Windows (bez wyłączania sprawdzania TLS):

```powershell
$env:MAVEN_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE'
.\mvnw.cmd spring-boot:run
```

API działa na `http://127.0.0.1:8091`. Frontend Vite przekazuje `/api` na ten adres; w katalogu głównym uruchom `npm run dev`.

## Dane

Baza developerska powstaje automatycznie w `backend/data/meeter-dev.mv.db`, pod warunkiem uruchamiania z katalogu `backend`. Dane przetrwają restart. Folder jest ignorowany przez Git. Nie trzeba instalować serwera bazy ani podawać hasła do rzeczywistego Meetera. Lokalny inicjalizator dodaje wyłącznie konto startowe i osobę opisaną poniżej.

Testy korzystają wyłącznie z H2 w pamięci. Docelową MariaDB/PostgreSQL i migracje schematu można dodać później. `ddl-auto=update` jest rozwiązaniem developerskim, nie systemem migracji produkcyjnych.

## API

### Przeglądanie ludzi

Widok **Ludzie** pomija osobę powiązaną z zalogowanym kontem (według Human ID, również dla administratora) przed wyszukiwaniem, zliczaniem i paginacją. Pozostałe osoby o takim samym imieniu i nazwisku pozostają widoczne. Wyświetla pozostałe osoby z tabeli `people`, również osoby bez właściciela i przypisane innym kontom, z profilówkami, liczbą wizyt oraz losowym publicznym cytatem. Sortuje według punktów malejąco, następnie alfabetycznie po nazwisku i imieniu oraz po ID. Uwzględnia również osoby bez interakcji (0 pkt). Licznik wizyt obejmuje własne wizyty, również zaplanowane, niezależnie od długości. Punkty pochodzą wyłącznie z interakcji zalogowanego konta; przyszłe daty punktowe jeszcze nie dają punktów.

Punktacja w `PeopleRanking` nie wymaga funkcji SQL z dawnej bazy: dla każdej interakcji `ROUND(POWER(0.5, liczba_dni / półtrwanie), 3) * waga`, potem suma. Dzisiaj jest lokalną datą w Europe/Warsaw. Wizyta: 6 pkt / 720 dni, data początku; spotkanie: 3 / 360, data początku; event: 1 / 180, osobiste wyjście lub koniec wydarzenia; podróż: 3 / 3650, data końca. Ślub: tylko para młoda i tylko `was_i_invited=true`; 3 pkt dla osoby w klice o nazwie WAGS (bez względu na wielkość liter i skrajne spacje), 15 pkt dla pozostałych, półtrwanie w obu przypadkach 1825 dni (5 × 365). Osoba bez kliki należy do pozostałych. Zwykli goście weselni i wspólne mieszkanie nie dostają punktów z tego tytułu. Ranking powstaje przed stronicowaniem. Metadane punktacji nie są zapisywane w bazie i przeliczają się przy odczycie.

`GET /api/people/browse?q=&page=0&size=20` zwraca `{content: [{person, visitCount, publicQuote}], page, size, totalElements, hasNext}`. `person` ma kształt dotychczasowego `HumanView`. Wyszukiwanie po imieniu i nazwisku traktuje `%` i `_` dosłownie, `page` jest nieujemne, `size` wynosi 1–100. Sortowanie działa na całym zbiorze przed stronicowaniem. Lista i endpoint `GET /api/people/browse/{id}/photo` wymagają uwierzytelnienia, lecz nie własności osoby. Dotychczasowe `GET /api/people?q=&limit=50` pozostaje bez zmian dla selektorów osób; zapis i zmiana zdjęcia nadal wymagają własności.

| Ścieżka | GET | POST |
| --- | --- | --- |
| `/api/places` | Lista / wyszukiwanie miejsc | `name`, `latitude`, `longitude`, `category` |
| `/api/cliques` | Lista / wyszukiwanie klik | `cliqueName`, `cliqueCapitalId` |
| `/api/people` | Lista / wyszukiwanie ludzi | `name`, `surname`, `birthdayDate`, `fbLink`, `gender`, `livesIn`, `homeId`, `cliqueId` |

GET przyjmuje `q` (fragment nazwy, bez rozróżniania wielkości liter) i `limit` (1–100, domyślnie 50). POST zwraca 201 i zapisany obiekt. Nieprawidłowe dane zwracają 400, nieistniejące lub cudze powiązanie 404. Opcjonalne powiązania pomiń lub przekaż jako `null`. Daty mają format `YYYY-MM-DD`. Wszystkie dane są filtrowane według zalogowanego właściciela.

Przykład kliki po utworzeniu miejsca o identyfikatorze 1:

```json
{ "cliqueName": "Przyjaciele", "cliqueCapitalId": 1 }
```

ID i UUID są generowane przez backend. Encje nie są bezpośrednio serializowane: DTO nie zawierają cyklicznych kolekcji. Członkostwo kliki jest reprezentowane przez `Human.clique`; nie przechowujemy drugiej kopii listy członków. Pola dla niezaimplementowanych jeszcze encji (zdjęcia, piosenki, interakcje, supermoce) zostaną dodane wraz z ich modelem. Punktacja interakcji docelowo należy do serwisu, nie encji człowieka.

## Logowanie

Przy pierwszym uruchomieniu lokalnego H2 powstaje osoba **Piotr Szkudlarek, ID 77** oraz powiązane konto **pszkudlarek**. Kolejne ID osób zaczynają się powyżej istniejącego maksimum. Inicjalizator nie nadpisze innej osoby pod ID 77 i nie zmieni hasła istniejącego konta. Dotychczasowe rekordy tej nowej bazy developerskiej bez właściciela są jednorazowo przypisywane temu kontu.

Losowe hasło startowe znajduje się wyłącznie w lokalnym `backend/data/initial-login.txt` (ignorowanym przez Git). Po zalogowaniu zmień hasło w interfejsie i usuń plik. Nie są importowane żadne rzeczywiste dane ani hasła ze starego Meetera. Inicjalizację można wyłączyć ustawiając `app.dev.bootstrap=false`.

| Endpoint | Działanie |
| --- | --- |
| `GET /api/auth/csrf` | Pobranie tokenu CSRF i nazwy nagłówka; publiczne |
| `POST /api/auth/login` | JSON `username`, `password`; publiczne, wymaga CSRF |
| `GET /api/auth/me` | Aktualne konto i powiązana osoba |
| `POST /api/auth/logout` | Unieważnienie bieżącego tokenu i usunięcie cookie |
| `POST /api/auth/password` | `currentPassword`, `newPassword`; unieważnia wszystkie sesje konta |

Token uwierzytelniający to 32 losowe bajty, wysyłane w cookie `HttpOnly`, `SameSite=Lax`, ścieżka `/api`, ważność 1 dzień (24 godziny od logowania), taka sama dla cookie i tokenu w bazie. Nowy czas dotyczy tokenów wystawionych po wdrożeniu; istniejące zachowują zapisany termin wygaśnięcia. Baza przechowuje wyłącznie SHA-256 tokenu. Hasła są przechowywane jako BCrypt z kosztem 12. Nowe hasło: co najmniej 12 znaków i najwyżej 72 bajty UTF-8. Tożsamość właściciela danych pochodzi z uwierzytelnienia, nigdy z JSON klienta.

Wszystkie operacje zmieniające dane wymagają nagłówka CSRF zgodnego z cookie. Frontend pobiera go automatycznie. Token uwierzytelniający nie trafia do localStorage ani odpowiedzi JSON. Pięć nieudanych prób na login blokuje go na 15 minut; dodatkowo obowiązuje limit 100 prób z adresu w tym oknie. Limiter jest w pamięci jednego procesu, więc resetuje się po restarcie; przy wielu instancjach należy przenieść go do współdzielonego magazynu.

Backend jest przypięty do loopback. Dla wdrożenia HTTPS ustaw `app.auth.secure-cookie=true`, wyłącz lokalny bootstrap i zaplanuj migracje schematu, zarządzanie kontami oraz odzyskiwanie hasła. Obecnie nie ma publicznej rejestracji ani API nadawania ról.

## Testy

```powershell
.\mvnw.cmd test
```

Testy HTTP sprawdzają zapis powiązanych encji, wyszukiwanie, walidację współrzędnych, nazw, dat i adresów, błędne identyfikatory, limity wyników, logowanie, CSRF, izolację właścicieli, wygasłe tokeny, logout, zmianę hasła i limitowanie prób.


Social Score pozostaje wyłącznie wewnętrznym wynikiem sortowania. Wartość punktów i wzór nie są prezentowane w UI ani zwracane w API, także administratorom. Publiczny cytat ma postać {id, quote} lub null; losowany jest wyłącznie z publicznych cytatów bieżącego autora.

Osoby z kliki my_exes (nazwa bez względu na wielkość liter i skrajne spacje) są pomijane przed liczeniem wyników, wyszukiwaniem i paginacją, także dla administratorów. Katalogowy endpoint profilówki zwraca dla nich 404. Pozostałe interfejsy zachowują swoje wcześniejsze reguły. Przy takim samym wyniku kolejność wyznaczają nazwisko, imię i ID.
