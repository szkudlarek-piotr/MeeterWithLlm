# Cytaty

Moduł `org.example.meeter.quote` zawiera `Quote`, `QuoteRepository`, `QuoteService`, `QuoteController` i kontrakty `QuoteModels`. Autor nowego cytatu to wymagana osoba `Human`; właściciel to zalogowane konto `User`, przypisywane przez serwer. Cytaty nie są interakcjami i nie trafiają do kalendarza.

## API

Podpowiedzi `GET /api/quotes/authors` są sortowane malejąco po liczbie cytatów widocznych dla bieżącego konta (publiczne, prywatne własnego autora według Human ID, wszystkie dla administratora). Prywatne cudze cytaty nie wpływają na kolejność zwykłego konta; cytaty bez autora nie podnoszą żadnego wyniku. Remisy rozstrzygają nazwisko, imię i ID. Wyszukiwanie, wykluczenie my_exes i sortowanie odbywają się przed LIMIT. Osoby bez cytatów nadal można wybrać. Liczby nie są zwracane w podpowiedziach.

- `POST /api/quotes`: utworzenie cytatu, odpowiedź 201.
- `GET /api/quotes/{id}`: dostępny cytat lub 404 dla nieistniejącego/niedostępnego ID.
Osobny endpoint kolekcji `GET /api/quotes` został usunięty. Cytaty są prezentowane w kafelkach ludzi i grze.

Przykład utworzenia:

```json
{
  "quote": "Słowa warte zapamiętania.",
  "authorId": 77,
  "isPublic": false
}
```

Treść po usunięciu skrajnych białych znaków ma mieć 4–10240 znaków. Zachowano minimum 4 z adnotacji przekazanej encji i poprawiono sprzeczny komunikat. `authorId` jest wymagane (brak/null: 400). Autor może pochodzić z dowolnego konta lub importu; nieistniejący i my_exes zwracają 404. GET /api/quotes/authors?q=...&limit=100 wyszukuje autorów niezależnie od właściciela, również osobę powiązaną z kontem. Zwraca tylko ID, nazwę i pusty URL zdjęcia. `isPublic` można pominąć, domyślnie false.

Odpowiedź cytatu zawiera `id`, `uuid`, `quote`, opcjonalne podsumowanie `author` (`id`, `fullName`, `profilePhotoUrl`), `isPublic` i serwerowy `addTime`. Dane konta, hasła ani tokeny nie są serializowane.

Wszystkie endpointy wymagają sesji. POST wymaga również CSRF. Publiczny cytat (`isPublic=true`) widzi każdy zalogowany użytkownik. Prywatny widzi konto powiązane z osobą będącą autorem oraz `ROLE_ADMIN`. Administrator może odczytać dowolny cytat przez endpoint pojedynczego rekordu. Prywatny cytat bez autora widzi zapisujące konto i administrator. Zapisanie prywatnego cytatu innej osoby samo w sobie nie daje zapisującemu prawa do późniejszego odczytu. Dane autora ograniczono do podsumowania; URL profilówki jest pomijany dla osoby nienależącej do konta oglądającego.

Role `ROLE_USER` i `ROLE_ADMIN` są przechowywane w `user_roles`. Role trafiają do DTO logowania i `/api/auth/me`, a tokenowy filtr uwierzytelniania używa ich jako authorities. `RoleBootstrap` uzupełnia `ROLE_USER` istniejącym kontom; przy lokalnym `app.dev.bootstrap=true` przypisuje kontu `pszkudlarek` także `ROLE_ADMIN`. Poza developmentem lokalny bootstrap należy wyłączyć. Nie ma publicznego endpointu nadawania ról.

`PUT /api/quotes/{id}/vote` przyjmuje `{vote: PLUS | MINUS}`, wymaga sesji i CSRF, a przyjmuje głosy tylko na cytaty widoczne dla danego konta. Konto może mieć jeden aktywny głos. Ponowienie tej samej oceny zwraca 204, a próba oddania przeciwnego głosu przed cofnięciem zwraca 409. DELETE /api/quotes/{id}/vote cofa własny głos i zwraca 204 także przy ponowieniu. Obowiązują te same zasady sesji, CSRF i widoczności. Migracja V5 zakłada tabelę głosów z unikalną parą cytat–konto.

## Interfejs i dane

Przełącznik „Tylko publiczne cytaty” obok motywu i w zakładce Cytaty zapisuje preferencję w cookie `meeter-public-quotes`. Gdy jest włączony, backend filtruje listę autora przed paginacją oraz zwraca 404 przy odczycie, ocenie i cofnięciu oceny prywatnego cytatu, również administratorowi i autorowi. Wyłączenie przywraca widoczność wynikającą z uprawnień konta.

Modal osoby ma zakładkę Cytaty: `GET /api/people/browse/{id}/quotes?page=0&size=20` (rozmiar 1–100). Zwraca standardowe pola strony i cytaty danego autora, sortowane przed paginacją malejąco według wyniku (plusy minus minusy), następnie według `addTime` i ID. Każdy wpis zawiera `score` i `myVote` (1, -1 lub null). Stosuje ten sam filtr widoczności co pojedynczy cytat: zalogowani widzą publiczne, konto powiązane z autorem oraz admin również prywatne. Liczność i paginacja obejmują wyłącznie widoczne cytaty. Nie przywraca globalnego endpointu kolekcji `/api/quotes`. Renderer pozostaje QuoteContent.

`src/QuoteContent.jsx` jest wspólnym rendererem cytatów dla listy ludzi i gry (pytanie oraz odpowiedź). Cytaty są wyśrodkowane. HTML jest parsowany w nieaktywnym elemencie template i przebudowywany jako elementy React z ograniczonej listy tagów formatujących: pogrubienie, kursywa, podkreślenie, przekreślenie, akapity, podziały wierszy, listy, indeksy, kod. Wszystkie atrybuty są odrzucane, w tym style i obsługa zdarzeń; skrypty, iframe, SVG i podobne aktywne treści są pomijane. Obrazy i odnośniki nie są osadzane; tekst odnośnika pozostaje. Nie wstawiaj surowego HTML przez dangerouslySetInnerHTML. Zwykły tekst i łamania wierszy pozostają obsługiwane. Dane źródłowe cytatu w bazie nie są zmieniane.

Zakładka Ludzie dołącza do każdej osoby co najwyżej jeden losowy **publiczny** cytat tej osoby jako `publicQuote: {id, quote}` (albo null) w `GET /api/people/browse`. Losowanie odbywa się przy każdym odczycie strony. Zapytanie ogranicza pulę do autorów na bieżącej stronie i `is_public=true` przed losowaniem; nie ma wyjątku dla administratora ani własnych prywatnych cytatów. Cytaty bez autora są pomijane. Cytat nie wpływa na ranking, liczność ani paginację ludzi. Widok nie wysyła osobnego żądania dla każdej osoby. Długie cytaty mają przewijane pole z zachowaniem łamań wierszy.

Menu zachowuje akcję „Dodaj cytat”, ale nie ma osobnego widoku kolekcji. Formularz zawiera treść, licznik znaków, wyszukiwarkę wymaganego autora, opcję „Cytat publiczny” oraz stany zapisu i błędu. Po zapisie otwiera listę ludzi i odświeża jej publiczne cytaty. Dane cytatów pozostają w bazie i są używane przez kafelki ludzi oraz grę.

Tabela `quotes` powstaje przez aktualną konfigurację developerską Hibernate. Tekst ma kolumnę o długości 10240, a nie domyślne 255. Autor i właściciel nie są usuwani kaskadowo wraz z cytatem. Przy rozbudowie o usuwanie osób/kont uwzględnij klucze obce z cytatów.
