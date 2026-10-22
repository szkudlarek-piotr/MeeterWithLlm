# Gra „Zgadnij autora”

Każdy zalogowany użytkownik uruchamia własną rozgrywkę. Wybiera jedną własną wizytę albo zestaw własnych klik (do 10). Wizyta daje pulę autorów ze wszystkich jej uczestników, także osoby dodawanej automatycznie; kliki dają sumę ich członków. Kliki pozostają prywatnym sposobem porządkowania kontaktów. Nie zmieniono ich modelu ani zasad dostępu.

## Losowanie

`QuoteGamePool` dla każdej osoby z wybranego zakresu pobiera **wyłącznie publiczne** cytaty z autorem. Losuje bez powtórzeń do dwóch cytatów danej osoby, następnie tasuje całą połączoną listę. Autor z jednym cytatem wnosi jeden, a autor bez publicznych cytatów — zero. Nie ma dodatkowego limitu całkowitej liczby pytań. Rola administratora nie dopuszcza prywatnych cytatów do gry. Cytaty bez autora nie trafiają do losowania.

Wynik losowania, kolejność, uczestnicy i kopie treści oraz autorów są zapisane w bazie. Odczyt, odświeżenie strony i wznowienie nie losują ponownie. Pusta pula zwraca 400 z informacją o potrzebie dodania publicznych cytatów. Jeśli cytat przestanie być publiczny, odczyt gry i dalsze odpowiedzi są blokowane, aby nie ujawniać jego zapisanej treści.

## Czas i odpowiedzi

Limit wynosi domyślnie 30 sekund na pytanie, konfigurowalnie 10–120 sekund. Pierwszy cytat startuje przy utworzeniu gry. Kolejne pytanie uruchamia osobny POST `next`, po przeczytaniu wyniku poprzedniej odpowiedzi. Serwer zapisuje `started_at` i `deadline_at`; powtórzenie `next` dla aktywnego pytania nie zeruje czasu. DTO zawiera zegar serwera do synchronizacji odliczania.

Przekroczenie terminu daje nietrafioną odpowiedź, także jeśli klient przyśle poprawnego autora po czasie. Brak odpowiedzi po terminie jest zapisywany jako timeout z pustym typem. Wznowienie przeterminowanego pytania zapisuje timeout; nie przyznaje nowego czasu. Samo opuszczenie ekranu aktywnego pytania nie zatrzymuje zegara.

`quote_guesses` przechowuje ID, rundę, wytypowaną osobę (opcjonalną dla timeoutu), poprawność, timeout, informację o własnym cytacie, czas odpowiedzi, datę zapisu i konto gracza. Jedna runda ma najwyżej jedną odpowiedź. Serwer sprawdza bieżącą rundę, przynależność typu do kandydatów i blokuje nadpisanie odpowiedzi. Blokada pesymistyczna rozgrywki i unikalność rundy w tabeli typów chronią przed równoczesnymi zapisami.

Pytanie nie zawiera ID cytatu ani autora. Poprawny autor jest ujawniany dopiero w wyniku zapisanej odpowiedzi. Ogólne API cytatów pozostaje dostępne zgodnie ze swoimi regułami; gra jest zabawą towarzyską, nie systemem egzaminacyjnym odpornym na podgląd całej publicznej kolekcji.

## Ranking

Wynik to `100 × trafione nieswoje cytaty / wszystkie nieswoje cytaty w rozgrywce`. Własność oznacza tu **autorstwo osoby powiązanej z kontem gracza**, a nie konto, które wprowadziło cytat. Własne cytaty nie zwiększają licznika ani mianownika; timeouty i błędne typy przy nieswoich cytatach obniżają wynik.

Tabela pokazuje najlepszą ukończoną rozgrywkę każdego konta, do 100 graczy. Wyższy procent wygrywa, przy remisie niższa suma czasu odpowiedzi na nieswoje cytaty. Dalsze remisy rozstrzyga termin ukończenia i ID gry. Gry zawierające tylko własne cytaty mają wynik procentowy null i nie są klasyfikowane. Rozgrywki w toku nie trafiają do rankingu.

Zalogowani użytkownicy widzą nazwę gracza, procent, liczbę trafień, liczbę klasyfikowanych cytatów i czas. Ranking nie ujawnia treści cytatów, prywatnych nazw wizyt/klik ani cudzych typów. Historia oraz szczegóły rozgrywek są dostępne wyłącznie ich właścicielowi; administrator nie otrzymuje tu automatycznego dostępu do cudzych odpowiedzi.

## API

| Metoda i ścieżka | Działanie |
| --- | --- |
| `POST /api/quote-games` | `{visitId, cliqueIds, secondsPerQuestion}`; dokładnie jeden rodzaj zakresu; odpowiedź 201 |
| `GET /api/quote-games?page=0` | Własna historia, po 20 gier |
| `GET /api/quote-games/{id}` | Wznowienie z aktualnym czasem i własnymi wynikami; zapisuje timeout, jeśli termin minął |
| `POST /api/quote-games/{id}/next` | Uruchomienie następnego pytania; body `{}` |
| `POST /api/quote-games/{id}/guesses` | `{roundId, authorId}`; `authorId=null` tylko po upływie terminu |
| `GET /api/quote-games/leaderboard` | Ranking ukończonych gier |

Wszystkie endpointy wymagają uwierzytelnienia; operacje POST także CSRF. Tworzenie z cudzej wizyty/kliki oraz odczyt cudzej gry zwracają 404. Zapis niebieżącej lub już zakończonej rundy zwraca 409. Tożsamość, poprawność, czas, punktacja i kolejność pochodzą z serwera.

## Model danych i testy

Moduł: `org.example.meeter.quote.game`. Encje: `QuoteGame`, `GameRound`, `QuoteGuess`, `GameCandidate`, `GameClique`. Powiązania zakresu i kandydatów mają własne ID, `created_at`, `created_by_id` przez istniejący `RelationshipRecord`. Usunięcie gry usuwa jej rundy, typy i powiązania, nie osoby, kliki ani cytaty. Testy muszą usuwać gry przed cytatami i ich autorami.

Interfejs: `src/QuoteGamePanel.jsx`, menu „Zgadnij autora”, wybór zakresu, licznik czasu, typowanie, wynik po odpowiedzi, wznowienie z historii oraz ranking.

Przy rozwoju wielu kont nie utożsamiaj ludzi po samym nazwisku. Dzisiejszy `Human` jest rekordem kartoteki właściciela; niezależne wpisy o tej samej realnej osobie wymagają jawnego modelu wspólnej tożsamości. Gra porównuje identyfikatory encji autorów z osobą powiązaną z kontem; nie scala automatycznie kartotek ani klik.
