# Interakcje Meetera

Źródło modeli: [Meeter_hibernate/interactions](https://github.com/szkudlarek-piotr/Meeter_hibernate/tree/master/src/main/java/org/example/meeter/interactions), odczytane 18 października 2026 r.

`Event` oznacza wydarzenie zewnętrzne, np. koncert, festiwal lub lekcję tańca. Zapisane przy nim osoby to ludzie spotkani na tym wydarzeniu, a nie kompletna lista uczestników. Wizyta, spotkanie, podróż, wspólne mieszkanie i ślub/wesele **nie są podtypami Event**.

Wszystkie sześć modeli dziedziczy po wspólnej klasie `Interaction`, która przechowuje ID, UUID, czas dodania, nazwę, opis, zakres dat, uczestników, opcjonalne miejsce i właściciela. JPA używa dziedziczenia `JOINED`: wspólna tabela `interactions` oraz osobne tabele sześciu typów. Każdy typ zachowuje własne pola.

## Różnice względem źródła

- UUID generuje się zawsze; w oryginale część klas pozostawiała go pustym.
- Wszyscy uczestnicy są przechowywani w inicjalizowanym zbiorze z unikalnością pary interakcja–osoba. Zalogowana osoba jest dodawana automatycznie. Nie trzeba jej wybierać w formularzu.
- Nazwa ma 2–128 znaków po obcięciu białych znaków. Opis jest opcjonalny, do 10240 znaków. Usunięto arbitralne wymagania co najmniej 16/32 znaków opisu.
- Długość wizyty jest liczbą całkowitą 1–3650, walidowaną przez `@Min/@Max`, nie `@Size`. Koniec jest wyliczany jako początek + liczba dni − 1, zgodnie z pierwotną semantyką.
- Koniec nie może poprzedzać początku. Przybycie i wyjście z eventu muszą mieścić się w jego granicach i być we właściwej kolejności. Daty przyjmują lata 1–9999; daty przyszłe są dozwolone dla planowania.
- `Flatmate.human` jest `ManyToOne`: ta sama osoba może mieć kilka odrębnych okresów wspólnego mieszkania. Koniec jest opcjonalny i oznacza trwający okres.
- Para młoda oraz miejsca ślubu są `ManyToOne`, zamiast ograniczających `OneToOne`. Ta sama sala może obsłużyć wiele ślubów, a osoba może wystąpić w wielu takich zapisach. Para musi składać się z dwóch różnych osób. Małżonkowie trafiają do uczestników automatycznie.
- Każda interakcja, w tym ślub i wspólne mieszkanie, ma właściciela. Obce osoby i miejsca nie mogą być przypisane; próby zwracają 404, tak samo jak nieistniejące rekordy.
- Encje nie są serializowane bezpośrednio. DTO nie mają cykli ani danych kont użytkowników.
- Feed ma paginację i sortuje według czasu dodania. Kalendarz pobiera pełny miesiąc, uwzględniając nakładające się zakresy i wszystkie strony wyników.
- Kliknięcie dnia w kalendarzu otwiera modal ze wszystkimi interakcjami przypadającymi na ten dzień, uczestnikami, opisami i zdjęciami. Modal obsługuje puste dni, ładowanie i błędy; zamyka się przez ×, Escape lub kliknięcie tła. Dodawanie spotkania z modalu zachowuje wybraną datę.
- Kalendarz pomija `FLATMATE` w kafelkach, modalu, liczniku i legendzie. Okresy wspólnego mieszkania pozostają w bazie, API i aktualnościach.
- W modalu autor interakcji może dodawać własne osoby. Uczestnika może usunąć on sam (według Human ID powiązanego z kontem) lub konto, które utworzyło jego powiązanie z interakcją. Przyciski wynikają z uprawnień zwracanych w DTO; backend sprawdza je niezależnie. Kontrakty i metadane opisuje [RELATIONSHIPS.md](RELATIONSHIPS.md).

Nie dodano nowych zasad naliczania punktów. Metoda daty punktowej dla czterech pierwotnych typów zachowuje ich semantykę, z bezpiecznym końcem eventu, jeśli nie podano własnego wyjścia. Event obsługuje opcjonalne zdjęcie z pliku, publicznego linku lub wcześniejszego własnego wydarzenia; szczegóły w [PHOTOS.md](PHOTOS.md).

## API tworzenia

### Zaproszenie na wesele i ranking ludzi

`WeddingInput` przyjmuje także `wasIInvited` (boolean, domyślnie false), zwracane jako `details.wasIInvited`. Formularz ślubu ma pole „Byłem / byłam zaproszony(-a) na wesele”, zaznaczone początkowo. `weddings.was_i_invited` jest niezależne od `had_after` (poprawiny). Historyczne rekordy bez tej kolumny otrzymują false; nie domyślamy się zaproszenia z obecności rekordu lub listy uczestników. Przy migracji przenieś oryginalne `was_i_invited` do tej kolumny. Przed aktualizacją schematu wykonaj kopię bazy zgodnie z AGENTS.md.

Ranking punktowy w zakładce Ludzie stosuje reguły opisane w README.md. Dla zaproszonych wesel punktowana jest para młoda przez relacje groom/bride, nawet jeśli nie ma jej na liście powiązań uczestników; zwykli goście nie są punktowani. WAGS jest rozpoznawane po nazwie kliki, nie po dawnym ID 5.

Każde żądanie zawiera wspólny obiekt:

```json
{
  "details": {
    "name": "Kawa po pracy",
    "longDesc": "Dobra rozmowa",
    "participantIds": [78, 79]
  }
}
```

`participantIds` jest wymagane, ale może być pustą listą. Powtórzone identyfikatory są usuwane. Wszystkie identyfikatory osób i miejsc muszą należeć do zalogowanego właściciela. Pozostałe pola zależą od endpointu:

| Endpoint | Pola poza `details` |
| --- | --- |
| `POST /api/meetings` | wymagane `date`; opcjonalne `placeId`, `placeString` |
| `POST /api/visits` | wymagane `visitDate`, `duration`; opcjonalne `placeId` |
| `POST /api/events` | wymagane `startDate`, `stopDate`; opcjonalne `meComingDate`, `meLeavingDate`, `placeId`, `placeString`, `photoName` oraz jedno źródło zdjęcia: `photoUrl`, `photoData` lub `photoEventId` |
| `POST /api/trips` | wymagane `startDate`, `endDate`, `placeIds` (może być puste) |
| `POST /api/flatmates` | wymagane `humanId`, `startDate`; opcjonalne `stopDate`, `placeId` |
| `POST /api/weddings` | wymagane `dateTime`, `groomId`, `brideId`; opcjonalne `ceremonyPlaceId`, `partyPlaceId`, `hotelId`, `hadAfter` |

Daty spotkań i wspólnego mieszkania: `YYYY-MM-DD`. Pozostałe daty: lokalne `YYYY-MM-DDTHH:mm:ss`, bez automatycznego przesunięcia do UTC (tak jak pierwotne `LocalDateTime`).

Przykład pełnego spotkania:

```json
{
  "details": { "name": "Kawa po pracy", "longDesc": null, "participantIds": [] },
  "date": "2026-10-18",
  "placeId": null,
  "placeString": "Stolik przy oknie"
}
```

## Odczyt

`GET /api/interactions` przyjmuje `page` (od 0), `size` (1–100), `q`, opcjonalne `from`, `to` oraz `type`: `MEETING`, `VISIT`, `EVENT`, `TRIP`, `FLATMATE`, `WEDDING`. Zwraca `content`, `page`, `size`, `totalElements`, `hasNext`.

Filtr dat zwraca zakresy **nakładające się** na podany przedział, a nie tylko wpisy zaczynające się w nim. Dla okresów trwających bez końca przyjmuje brak górnej granicy.

`GET /api/interactions/{id}` zwraca własny rekord albo 404. Odpowiedź zawiera wspólne pola, podsumowania uczestników, miejsce oraz obiekt `details` z polami konkretnego typu. Wszystkie operacje tworzenia wymagają sesji i CSRF.

## Profil osoby

DTO pojedynczego profilu (`GET /api/people/browse/{id}`), w tym dom i stolica kliki, jest budowane w transakcji MeeterService. Nie mapuj encji zwróconej przez browseHuman w kontrolerze: przy wyłączonym open-in-view relacje lazy nie są wtedy dostępne i profil może kończyć się błędem zależnie od przypisanych relacji.

Modal w katalogu ludzi zawiera profil, mapę interakcji, stronicowane wizyty, spotkania, eventy, podróże i cytaty. `GET /api/people/browse/{id}/interactions` przyjmuje `type`, `page`, `size` (1–100), sortuje po dacie interakcji malejąco i zwraca standardowe PageView. Dotyczy wyłącznie interakcji z daną osobą na liście uczestników. Dostęp ma autor zapisu, uczestnik rozpoznany przez Human ID konta lub administrator. Filtr widoczności działa przed liczeniem i paginacją. Profil jest tylko do odczytu; nie rozszerza zwykłego `/api/interactions` ani praw modyfikacji. Zdjęcia cudzych eventów nie są dołączane, ponieważ ich endpoint pozostaje owner-only.

`GET /api/people/browse/{id}/map` stosuje ten sam filtr do wszystkich wyników, niezależnie od strony listy. Zwraca punkty zapisanych miejsc wizyt, spotkań, eventów i wszystkich miejsc podróży, z nazwą i kategorią miejsca, bez nazw interakcji ani dat. Każde miejsce występuje tylko raz (deduplikacja po Place ID), także gdy pojawia się w wielu interakcjach. Nie geokoduje opisów tekstowych. Leaflet pokazuje dostępne punkty i listę; każda podróż ma również własną mapę. `FLATMATE` i `WEDDING` pozostają poza tym profilem interakcji. Katalogowe wykluczenie `my_exes` obowiązuje też dla endpointów profilu.

## Mapa podróży

Modal dnia w kalendarzu pokazuje dla podróży interaktywną mapę Leaflet z punktami zapisanych miejsc i listą ich nazw. Widok automatycznie obejmuje wszystkie prawidłowe współrzędne; kliknięcie punktu pokazuje nazwę. Mapa korzysta z kafelków OpenStreetMap i wymaga internetu. Przy błędzie pobierania wyświetla komunikat, a lista miejsc pozostaje dostępna. Pusty zbiór miejsc ma osobny komunikat. Kolory kontrolek i obramowanie pasują do obu motywów. Punkty nie oznaczają kolejności odwiedzin ani przebiegu drogi; model przechowuje zbiór miejsc.
