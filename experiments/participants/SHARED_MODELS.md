# Wspólne modele uczestników

Z katalogu projektu uruchom w PowerShellu:

```powershell
.\experiments\participants\train-shared.ps1
```

Skrypt działa samodzielnie: tworzy `.venv`, jeśli go brakuje, sprawdza zależności,
wczytuje `set_agent_sandbo_env.ps1`, odczytuje spójny snapshot MariaDB/MySQL
w transakcji read-only i trenuje trzy modele. Nie odczytuje `history.json`, nie
eksportuje rekordów do pliku i nie zmienia bazy. Python musi być zainstalowany
(polecenie `py.exe` lub `python.exe`); pierwsze pobranie RoBERTy wymaga internetu.
Przed uruchomieniem sprawdź lokalny skrypt konfiguracji bazy. Wartości haseł
i rekordy nie są wypisywane przez trening. Lista zależności zawiera wyłącznie
nazwy i wersje pakietów, bez adresów źródeł instalacji.

Domyślnie używa dwóch wątków CPU. Opcjonalnie:

```powershell
.\experiments\participants\train-shared.ps1 -Threads 4
# Przy już zainstalowanych zależnościach:
.\experiments\participants\train-shared.ps1 -SkipDependencies
```

Wyniki w `experiments/participants/artifacts/`:

- `shared_logistic.joblib` — wspólna regresja logistyczna;
- `shared_extra_trees.joblib` — wspólne Extra Trees;
- `shared_boosting.joblib` — wspólny boosting;
- `shared_metrics.json` — metryki walidacji/testu i wybór zalecanego algorytmu;
- `shared-training-*.log` — zbiorczy przebieg treningu;
- `shared-requirements-lock.txt` — wersje zależności do odtworzenia eksperymentu.

Każdy z trzech modeli obsługuje nazwę/miejsce oraz nazwę/miejsce/gości. Trening
łączy przykłady bez gości z dwoma losowymi maskowaniami gości; kandydat będący
znanym gościem nie jest własnym celem ani negatywem. Cechy każdej pary pochodzą
z wcześniejszych dni, osobno dla konta. Konta nie mają osobnych wag; nie są
też cechami modelu. Model dostaje statystyki relacji i wektory tekstu.
Nowe artefakty nie zawierają historii, identyfikatorów kont/osób ani surowych tekstów.

## Podział i interpretacja wyników

Domyślny `-SplitKey start_date` dzieli około 60/20/20 według liczby rekordów,
z zachowaniem całych lokalnych dni. Rozwiązuje problem importów z prawie
identycznymi datami `created_at`. To przybliżony backtest według dat wydarzeń:
nie odtwarza czasu dostępności danych, dawnych edycji ani składu gości w przeszłości.
Opcja `-SplitKey created_at` ocenia dostępność według dni utworzenia, jeśli
w bazie jest co najmniej pięć różnych dni; import może uniemożliwić ten podział.

Walidacja i test używają wyłącznie historii części treningowej. Przykłady jednego
dnia nie wpływają wzajemnie na swoje cechy. `recall@k`, `ndcg@k` i
`candidate_coverage` są liczone osobno dla `context` i `guests`. Nowe osoby
pozostają w mianowniku Recall, a konto bez historii otrzymuje pustą listę.
Zalecany model wybierany jest według średniego NDCG obu trybów na walidacji.
Nie wykorzystujemy wyniku testu do wyboru lub strojenia modelu.

Po ocenie te same ustawienia są ponownie trenowane na całym snapshocie i dopiero
te modele są zapisywane do użycia w aplikacji. Raport opisuje wcześniejszy backtest,
nie wynik końcowego modelu na własnych danych treningowych. Wspólny format działa
również wtedy, gdy aktualna baza ma dane tylko jednego konta; korzyści uczenia
między kontami wymagają historii wielu kont. Rekordy bez powiązanego twórcy-konta
nie są przypisywane do wymyślonego użytkownika.

## Użycie w aplikacji

### Czas i dzień tygodnia (format modeli 3)

Nowy trening zapisuje `schema_version=3` oraz `half_life_days` (domyślnie 365).
Spotkanie sprzed `d` lokalnych dni wnosi wagę `2 ** (-d / half_life_days)`.
Wcześniejszą popularność proporcjonalną i udział miejsca w historii danej osoby
zastępują sumy ważonych spotkań: ogółem i w którymkolwiek z wybranych miejsc.
Dodatkowe cechy to takie sumy w dniu tygodnia planowanej interakcji, również
w połączeniu z miejscem, oraz siedem wskaźników dnia tygodnia (poniedziałek–niedziela).
Spotkania w innym miejscu nie zmniejszają sumy dla wybranego miejsca.
Jedna interakcja wnosi najwyżej jedną wagę na osobę, również przy wielu miejscach.
Pozostałe cechy tekstu i współuczestnictwa pozostają dostępne modelowi.

Datą odniesienia jest lokalna data początku z formularza lub interakcji w kalendarzu.
Starszy klient bez daty używa daty istniejącej interakcji, a przy tworzeniu — dzisiejszej.
Uwzględniana jest tylko historia z dni wcześniejszych od daty odniesienia, także
dla cech tekstowych i współuczestnictwa. Ten sam dzień jest wykluczony, ponieważ
model nie rozstrzyga kolejności godzin. Dla interakcji wielodniowej liczy się data początku.
Zmiana daty formularza odświeża propozycje, bez konwersji do UTC.

Okres połowicznego zaniku można ustawić przy ręcznym treningu:

```powershell
.\experiments\participants\train-shared.ps1 -HalfLifeDays 365
```

Zmiana wymaga jawnego ponownego treningu i restartu backendu. Same zmiany kodu
nie zmieniają istniejących wag: modele formatu 2 i starsze modele kontowe nadal
korzystają z poprzednich cech. Nie trenuj automatycznie przy starcie. Wartość
365 dni jest ustawieniem początkowym, nie wynikiem strojenia na danych użytkownika.

Po zakończeniu treningu zrestartuj backend (nie trzeba osobno uruchamiać Pythona).
Backend czeka na gotowość procesu i wstępnie załadowane wagi. Zalecany model
z raportu jest domyślnie wybrany w formularzu. Podpowiedzi pojawiają się automatycznie
po wpisaniu nazwy i odświeżają po zmianie miejsca, gości lub modelu (700 ms przerwy).
Są również dostępne przy dopisywaniu uczestników w szczegółach dnia kalendarza.
Kliknięcie propozycji dodaje osobę do wyboru; zapis pozostaje osobnym działaniem.
W formularzu kliknij „Sprawdź ponownie”, wybierz algorytm i tryb.
Sześć pozycji w selektorze to trzy wspólne modele używane w dwóch trybach,
bez dodatkowych plików dla użytkowników. Wspólne modele mają pierwszeństwo
przed starymi plikami kont; stare pliki pozostają zachowane.

Backend tworzy historię wejściową z interakcji utworzonych przez zalogowane konto,
wyklucza jego powiązaną osobę i dostarcza ją workerowi przez lokalne,
uwierzytelnione połączenie. Frontend nie przekazuje konta ani historii. Worker
nie otrzymuje danych logowania do bazy. Przy zmianach danych backend dostarcza
aktualną historię. Wektory tekstu i historie są buforowane tylko w pamięci,
z limitami 4096 tekstów na encoder i 32 snapshotów historii. Kandydaci są
wyłącznie z historii bieżącego konta; ID osób nie są scalane po nazwach.

Pierwsze podpowiedzi wymagają zakodowania historii RoBERTą; na CPU może to
potrwać i przekroczyć limit żądania 120 sekund. Po zakończeniu tego obliczenia
ponowienie użyje bufora. Duże historie mogą przekroczyć limit wewnętrznego
żądania 16 MB. To ograniczenia obecnego lokalnego prototypu.

Nowe interakcje odświeżają cechy, ale nie zmieniają wag modelu. Ponownie uruchom
ten sam skrypt, gdy chcesz przetrenować wspólne parametry. Po ponownym treningu
zrestartuj backend, aby usunąć bufor poprzednich modeli. Nie ładuj cudzych plików joblib.
