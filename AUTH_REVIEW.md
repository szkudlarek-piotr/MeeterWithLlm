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
| Dowolny zalogowany użytkownik może wywołać pokazane endpointy tworzenia kont i ról | Tych endpointów nie wystawiamy; pierwsze konto tworzy lokalny bootstrap |
| Przyjmowanie użytkownika jako `Map<String,String>` bez walidowanego kontraktu | Walidowane rekordy wejściowe oraz osobne DTO odpowiedzi |
| Brak limitowania prób logowania w pokazanym module | Limit na login i adres, generyczny komunikat dla złego hasła i nieistniejącego konta |
| Równoległe implementacje hashowania w `UserService` i `PasswordEncoder` | Jeden `PasswordEncoder` BCrypt, koszt 12 |
| Brak pokazanej zmiany hasła i odwołania pozostałych sesji | Zmiana hasła unieważnia wszystkie tokeny konta |
| Przekierowania formularza do `/` | Odpowiedzi JSON 200/401/403/429 dla Reacta |

Powiązanie konta z człowiekiem jest w nowym modelu jeden do jednego. `User` i tokeny nigdy nie są bezpośrednio serializowane. Każde miejsce, klika i osoba ma właściciela, a odczyt i przypisanie relacji są ograniczone do jego rekordów. Próba wskazania cudzego miejsca lub kliki daje takie samo 404 jak nieistniejący rekord.

Zachowana została koncepcja tokenów w bazie i cookie ze źródłowego projektu; implementacja została dopasowana do nowego API. Nie skopiowano pozostałych danych, haseł, konfiguracji ani schematu rzeczywistej bazy. Nie dodano JWT: do lokalnej aplikacji odwoływalne, losowe tokeny są wystarczające.

## Ograniczenia

To nadal lokalny backend developerski: brak odzyskiwania hasła, 2FA, zarządzania kontami i współdzielonego limitera. H2 i automatyczna aktualizacja schematu nie zastępują docelowej bazy i migracji. Plik hasła startowego należy usunąć po zmianie hasła. Publiczne wdrożenie wymaga HTTPS i cookie `Secure`.
