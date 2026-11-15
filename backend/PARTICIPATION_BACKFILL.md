# Udział w interakcjach i uzupełnianie powiązań

## Ranking według uczestnictwa

Katalog ludzi, kolejność członków klik i ranking klik korzystają z interakcji, w których osoba powiązana z bieżącym kontem ma rekord w `interaction_human`. Twórca interakcji może być dowolny albo pusty. Samo autorstwo bez uczestnictwa nie wystarcza; usunięcie własnego powiązania usuwa wkład tej interakcji z rankingu. Tożsamość jest sprawdzana po Human ID, nigdy po nazwie osoby. `EXISTS` chroni przed mnożeniem wkładów. Licznik wspólnych wizyt i kontekstowe podpowiedzi osób stosują ten sam zakres uczestnictwa.

Wagi, zanik czasowy, zaokrąglenia i pomijanie przyszłych dat pozostają bez zmian. Wesela nadal punktują wyłącznie parę młodą przy `was_i_invited=true`, teraz dodatkowo wymagając własnego powiązania uczestnika. Goście nie dostają punktów weselnych. Dla wydarzenia utworzonego przez oglądającego datą punktową jest jego `me_leaving_date` lub koniec wydarzenia. Przy cudzym wydarzeniu używany jest koniec wydarzenia, ponieważ pola osobistego przybycia/wyjścia dotyczą twórcy i nie ma ich odpowiedników dla każdego uczestnika. Wspólne mieszkanie nadal nie wpływa na Social Score.

Zakres odczytu i modyfikacji zwykłych API nie zostaje poszerzony. W szczególności uczestnictwo nie daje prawa do zwykłego GET/PUT interakcji ani zdjęć wydarzenia; istniejące wyjątki profilu osoby działają jak wcześniej. Wartości rankingu pozostają wewnętrzne i nie są logowane ani zwracane w API.

## Jawne narzędzie administracyjne

W **Administracja → Uzupełnij mój udział w interakcjach** administrator najpierw wybiera „Sprawdź brakujące powiązania”. Uwierzytelniony `GET /api/admin/participation-backfill` pokazuje tylko liczniki brakujących wpisów dla spotkań, wizyt, wydarzeń, podróży oraz wesel z flagą zaproszenia. Ten odczyt niczego nie zapisuje.

Przycisk „Dodaj mnie do … interakcji” wywołuje `POST /api/admin/participation-backfill` z sesją i CSRF. Nie przyjmuje Human ID ani twórcy od klienta. Dopisywana jest wyłącznie osoba powiązana z kontem wykonującym operację, również do interakcji importowanych, bez właściciela i zapisanych przez inne konta. To wąski, jawnie zamówiony wyjątek administracyjny; zwykły endpoint dodawania uczestników zachowuje wcześniejsze uprawnienia.

Zakres obejmuje wszystkie daty spotkań, wizyt, wydarzeń i podróży, w których brakuje własnego powiązania. Na wyraźne życzenie użytkownika wesela są wybierane przez `was_i_invited=true`; to reguła tego narzędzia, a nie ogólne utożsamienie zaproszenia z obecnością. Nie zmienia ono flag zaproszenia ani innych danych wesela. Wspólne mieszkanie jest pomijane.

Operacja może odtworzyć wcześniej jawnie usunięty własny udział — UI informuje o tym przed zapisem. Nie jest uruchamiana przez start aplikacji, odczyt rankingu, Flyway ani synchronizację Neo4j. Nie została automatycznie wykonana na danych użytkownika podczas implementacji. Przed bulkową konserwacją danych wykonaj kopię bazy zgodnie z AGENTS.md. Nie ma zmiany schematu.

Rodzice są blokowani w kolejności ID; własne powiązanie jest ponownie sprawdzane po blokadzie. Nowy rekord otrzymuje wygenerowane ID, bieżący czas i `created_by_id` działającego konta. Istniejące rekordy zachowują ID, twórcę i czas, a osoby i właściciele interakcji nie zmieniają się. Cały zapis jest jedną transakcją: błąd wycofuje wszystkie nowe powiązania. Ponowienie pomija już istniejące. Wynik zawiera `added` według typu i `skipped` dla rekordów równolegle zmienionych/usuniętych. Dostęp wymaga ROLE_ADMIN także na backendzie.

Po uzupełnieniu danych istniejący proces synchronizacji Neo4j przeniesie nowe powiązania spotkań, wizyt, wydarzeń i podróży podczas kolejnego przebiegu. Wesela pozostają poza obecną synchronizacją grafu. Samo narzędzie nie zapisuje do Neo4j.
