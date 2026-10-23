# Cytaty

Moduł `org.example.meeter.quote` zawiera `Quote`, `QuoteRepository`, `QuoteService`, `QuoteController` i kontrakty `QuoteModels`. Autor nowego cytatu to wymagana osoba `Human`; właściciel to zalogowane konto `User`, przypisywane przez serwer. Cytaty nie są interakcjami i nie trafiają do kalendarza.

## API

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

## Interfejs i dane

Modal osoby ma zakładkę Cytaty: `GET /api/people/browse/{id}/quotes?page=0&size=20` (rozmiar 1–100). Zwraca standardowe pola strony i cytaty danego autora, sortowane po `addTime`, potem ID malejąco. Stosuje ten sam filtr widoczności co pojedynczy cytat: zalogowani widzą publiczne, konto powiązane z autorem oraz admin również prywatne. Liczność i paginacja obejmują wyłącznie widoczne cytaty. Nie przywraca globalnego endpointu kolekcji `/api/quotes`. Renderer pozostaje QuoteContent.

`src/QuoteContent.jsx` jest wspólnym rendererem cytatów dla listy ludzi i gry (pytanie oraz odpowiedź). Cytaty są wyśrodkowane. HTML jest parsowany w nieaktywnym elemencie template i przebudowywany jako elementy React z ograniczonej listy tagów formatujących: pogrubienie, kursywa, podkreślenie, przekreślenie, akapity, podziały wierszy, listy, indeksy, kod. Wszystkie atrybuty są odrzucane, w tym style i obsługa zdarzeń; skrypty, iframe, SVG i podobne aktywne treści są pomijane. Obrazy i odnośniki nie są osadzane; tekst odnośnika pozostaje. Nie wstawiaj surowego HTML przez dangerouslySetInnerHTML. Zwykły tekst i łamania wierszy pozostają obsługiwane. Dane źródłowe cytatu w bazie nie są zmieniane.

Zakładka Ludzie dołącza do każdej osoby co najwyżej jeden losowy **publiczny** cytat tej osoby jako `publicQuote: {id, quote}` (albo null) w `GET /api/people/browse`. Losowanie odbywa się przy każdym odczycie strony. Zapytanie ogranicza pulę do autorów na bieżącej stronie i `is_public=true` przed losowaniem; nie ma wyjątku dla administratora ani własnych prywatnych cytatów. Cytaty bez autora są pomijane. Cytat nie wpływa na ranking, liczność ani paginację ludzi. Widok nie wysyła osobnego żądania dla każdej osoby. Długie cytaty mają przewijane pole z zachowaniem łamań wierszy.

Menu zachowuje akcję „Dodaj cytat”, ale nie ma osobnego widoku kolekcji. Formularz zawiera treść, licznik znaków, wyszukiwarkę wymaganego autora, opcję „Cytat publiczny” oraz stany zapisu i błędu. Po zapisie otwiera listę ludzi i odświeża jej publiczne cytaty. Dane cytatów pozostają w bazie i są używane przez kafelki ludzi oraz grę.

Tabela `quotes` powstaje przez aktualną konfigurację developerską Hibernate. Tekst ma kolumnę o długości 10240, a nie domyślne 255. Autor i właściciel nie są usuwani kaskadowo wraz z cytatem. Przy rozbudowie o usuwanie osób/kont uwzględnij klucze obce z cytatów.
