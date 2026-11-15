# Przegląd i adaptacja logowania

Źródło: [moduł auth Meetera](https://github.com/szkudlarek-piotr/Meeter_hibernate/tree/master/src/main/java/org/example/meeter/auth), odczytany 18 października 2026 r. Ocena dotyczy wskazanego modułu, nie całej aplikacji ani jej infrastruktury.

## Co jest dobre

- BCrypt do haseł.
- Tokeny generowane z 32 bajtów `SecureRandom`.
- Cookie `HttpOnly`, `SameSite=Lax` i obsługa `Secure`.
- Ważność tokenu **jest sprawdzana** przez zapytanie `TokenRepository` — filtr nie musi robić tego ponownie.
- Oddzielenie encji użytkownika od encji człowieka.

## Zmiany w nowym backendzie

| Obserwacja w źródle | Nowe zachowanie |
| --- | --- |
| Token zapisany w bazie w postaci umożliwiającej użycie jako cookie | Wyłącznie SHA-256 tokenu w bazie; sam token tylko w cookie |
| CSRF wyłączone mimo logowania przez automatycznie wysyłane cookie | Ochrona CSRF obejmuje także logowanie, wylogowanie i zmianę hasła |
| Brak własnego unieważniania tokenu przy logout w pokazanym module | Jawny endpoint usuwa token z bazy; replay po logout zwraca 401 |
| Dowolny zalogowany użytkownik może wywołać pokazane endpointy tworzenia kont i ról | Pierwsze konto tworzy lokalny bootstrap; dodatkowe konta może tworzyć wyłącznie administrator, bez nadawania roli administratora — patrz ADMIN_USERS.md |
| Przyjmowanie użytkownika jako `Map<String,String>` bez walidowanego kontraktu | Walidowane rekordy wejściowe oraz osobne DTO odpowiedzi |
| Brak limitowania prób logowania w pokazanym module | Limit na login i adres, generyczny komunikat dla złego hasła i nieistniejącego konta |
| Równoległe implementacje hashowania w `UserService` i `PasswordEncoder` | Jeden `PasswordEncoder` BCrypt, koszt 12 |
| Brak pokazanej zmiany hasła i odwołania pozostałych sesji | Zmiana hasła unieważnia wszystkie tokeny konta |
| Przekierowania formularza do `/` | Odpowiedzi JSON 200/401/403/429 dla Reacta |

Powiązanie konta z człowiekiem jest w nowym modelu jeden do jednego. `User` i tokeny nigdy nie są bezpośrednio serializowane. Każde miejsce, klika i osoba ma właściciela, a odczyt i przypisanie relacji są ograniczone do jego rekordów. Próba wskazania cudzego miejsca lub kliki daje takie samo 404 jak nieistniejący rekord.

Zachowana została koncepcja tokenów w bazie i cookie ze źródłowego projektu; implementacja została dopasowana do nowego API. Nie skopiowano pozostałych danych, haseł, konfiguracji ani schematu rzeczywistej bazy. Nie dodano JWT: do lokalnej aplikacji odwoływalne, losowe tokeny są wystarczające.

## Trwała historia uwierzytelniania

Migracja `V7__auth_event_history.sql` tworzy `auth_events`, niezależne od `auth_tokens`. Rejestrujemy `LOGIN_SUCCESS`, `LOGIN_FAILURE` (złe hasło lub nieistniejący login), `LOGIN_THROTTLED`, jawne `LOGOUT` oraz `PASSWORD_CHANGED`. Wpis zawiera serwerowy `occurred_at` (`Instant`), opcjonalny `account_id`, znormalizowany login, opcjonalny `session_record_id`, IP połączenia i User-Agent do 512 znaków. Dla nieznanego loginu i próby zatrzymanej przez limiter konto jest null. Identyfikator rekordu sesji pozwala połączyć sukces logowania z wylogowaniem, ale nie jest poświadczeniem. Nie zapisujemy haseł, tokenów, ich skrótów, cookies, treści żądania ani szczegółów błędu.

IP pochodzi z `request.getRemoteAddr()`, bez samodzielnego ufania nagłówkom `X-Forwarded-For`. Przy reverse proxy wymagana jest świadoma konfiguracja zaufanej infrastruktury. User-Agent jest niezaufaną, ograniczoną długością etykietą; nie dowodzi tożsamości urządzenia. Znaki sterujące w etykietach są zamieniane na spacje.

Nieudane logowanie i odrzucenie przez limiter są zapisywane przez osobny bean z `REQUIRES_NEW`, dzięki czemu rollback logowania nie usuwa zdarzenia. Sukces, logout i zmiana hasła zapisują audyt atomowo ze zmianą sesji/hasła. Błąd zapisu audytu blokuje sukces operacji, zamiast udawać kompletną historię. Tabela nie ma FK ani kaskady do kont i tokenów; przechowuje historyczne identyfikatory i login. Usunięcie sesji, sprzątanie wygasłych tokenów i usunięcie konta nie kasują historii. Nie ma automatycznej retencji, publicznego endpointu ani UI historii; dostęp operacyjny jest przez uprawnienia bazy. Nie dodajemy wpisów przy każdym uwierzytelnionym żądaniu.

Zakres obejmuje próby, które dotarły do sprawdzania poświadczeń/limitera. Żądania odrzucone wcześniej przez CSRF lub walidację JSON oraz naturalne wygaśnięcie tokenu nie są zdarzeniami tego rejestru. Ponowne logowanie i zmiana hasła nadal unieważniają poprzednie sesje według dotychczasowych zasad. Wcześniejszych zdarzeń nie odtwarzamy z istniejących tokenów. Przed uruchomieniem wersji z V7 zatrzymaj backend i wykonaj backup.

## Pozostałe ograniczenia

Obsługa wewnętrznego dispatchu servletowego `ERROR` jest dozwolona, żeby pierwotny błąd serwera zachował status 500 zamiast zostać zastąpiony przez 401 przy renderowaniu `/error`. Bezpośrednie żądanie HTTP do `/error` nadal jest zabronione; endpointy API wymagają uwierzytelnienia. Frontend interpretuje 401 jako utratę sesji, więc błędy aplikacji nie mogą być maskowane tym statusem. Treść odpowiedzi błędu nadal nie zawiera stack trace ani komunikatu wyjątku.

To nadal lokalny backend developerski: brak odzyskiwania hasła, 2FA, pełnego zarządzania kontami i współdzielonego limitera. H2 i automatyczna aktualizacja schematu nie zastępują docelowej bazy i migracji. Plik hasła startowego należy usunąć po zmianie hasła. Publiczne wdrożenie wymaga HTTPS i cookie `Secure`.
