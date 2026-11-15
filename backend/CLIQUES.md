# Widok klik i import zdjęć

Zakładka **Kliki** korzysta z uwierzytelnionego `GET /api/cliques/browse?page=0&size=12` (maksymalnie 100 klik na stronę). Pokazuje kliki należące do konta, malejąco według średniej kwadratów wewnętrznych punktów członków. Wynik korzysta z PeopleRanking.SCORES, uwzględnia członków bez historii z wartością zero i pomija zalogowaną osobę zarówno w sumie, jak i liczbie członków. Puste kliki otrzymują zero. Remisy rozstrzyga ID rosnąco. Ranking jest wyliczany globalnie przed paginacją i nie jest zwracany w API ani prezentowany w UI. Nie zmienia prywatności klik ani istniejącego selektora `/api/cliques`. `my_exes` jest pomijane. Członkowie bieżącej strony są pobierani zbiorczo, bez limitu 100 osób: kolejność pochodzi z tego samego `PeopleRanking.SCORES` co katalog ludzi (punkty malejąco, nazwisko, imię, ID). Zalogowana osoba i `my_exes` są pomijane tak samo jak w katalogu. Wynik nie ujawnia punktów. Własność członków nie ogranicza wspólnego katalogu osób; własność kliki nadal ogranicza jej odczyt.

Na szerokim ekranie są trzy karty w rzędzie (dwie poniżej 900 px, jedna poniżej 700 px). Karta pokazuje kwadratowe zdjęcie kliki 96×96 px z zaokrąglonymi rogami, bez kadrowania (object-fit: contain), pod nim wyśrodkowaną nazwę, a niżej miniaturki członków 28×28 px bez podpisów. Imię i nazwisko członka pojawia się w podpowiedzi po najechaniu na jego awatar. Brak pliku lub profilówki ma stan zastępczy. Widok obsługuje ładowanie, błąd, ponowienie i paginację.

Portrety członków rozświetlają się po najechaniu i przy fokusie klawiatury. Kliknięcie lub Enter/Spacja otwiera istniejący modal profilu osoby z zakładkami interakcji, mapy i cytatów. Modal zamyka się przyciskiem, Escape lub kliknięciem poza nim. Zmiana zdjęcia w profilu odświeża również miniatury tej osoby w widoku klik.

## Wzór sortowania klik

Dla kliki $C$ wyznaczamy zbiór jej członków $M_C$, pomijając osobę powiązaną z zalogowanym kontem. Wynik kliki to **średnia kwadratów Social Score jej członków**:

$$
R(C) =
\begin{cases}
\displaystyle \frac{1}{n_C}\sum_{h \in M_C} \bigl(SS(h)\bigr)^2, & n_C > 0, \\
0, & n_C = 0.
\end{cases}
$$

Gdzie:

- $M_C$ — członkowie kliki bez osoby powiązanej z bieżącym kontem;
- $n_C = |M_C|$ — liczba tych członków, również osób bez historii wspólnych interakcji;
- $SS(h)$ — bieżący Social Score osoby $h$, wyliczany przez `PeopleRanking.SCORES` z interakcji, w których uczestniczy osoba powiązana z kontem oglądającego. Wagi, zanik czasowy i zasady wesel opisuje [README.md](README.md), a zakres uczestnictwa — [PARTICIPATION_BACKFILL.md](PARTICIPATION_BACKFILL.md).

Brak punktowanych interakcji oznacza $SS(h)=0$. Taka osoba pozostaje w mianowniku. Własna osoba jest wyłączona zarówno z sumy, jak i z liczby członków. Klika pusta lub zawierająca wyłącznie własną osobę otrzymuje wynik zero.

Kwadrat liczymy osobno dla każdej osoby, a następnie sumujemy i dzielimy przez liczbę członków. Nie jest to kwadrat średniej; nie dzielimy też wyniku ponownie przez liczebność kliki. Przykład na umownych wartościach: dla wyników członków $10$ i $0$ wynik kliki wynosi $(10^2+0^2)/2=50$, a dla $5$ i $5$ wynosi $(5^2+5^2)/2=25$. Reguła mocniej premiuje pojedyncze bliskie relacje, jednocześnie uwzględniając wszystkich członków grupy.

Kliki sortujemy malejąco po $R(C)$, a przy remisie rosnąco po ID. Ranking obejmuje cały dostępny zbiór przed paginacją. Kolejność osób wewnątrz kliki nadal wynika z ich indywidualnego Social Score, z remisami rozstrzyganymi przez nazwisko, imię i ID.

Wynik i wzór są opisem technicznym: nie są zwracane przez API, logowane jako dane użytkownika ani prezentowane w interfejsie aplikacji.

## Przygotowanie schematu

`V4__clique_photos.sql` rozszerza typ zdjęcia o `CLIQUE` i dodaje `cliques.photo_id` wskazujące metadane `photos`. Nie importuje danych osobowych ani plików. Przed startem backendu z nowym kodem zatrzymaj poprzedni backend i wykonaj kopię MariaDB zgodnie z [MIGRATIONS.md](MIGRATIONS.md). Nie edytuj zastosowanych migracji. Testy używają H2 i nie migrują bazy użytkownika.

## Narzędzie administratora

W menu **Administracja → Import zdjęć klik** wklej ścieżkę folderu dostępnego na serwerze backendu. Narzędzie przyjmuje pliki `ID.jpg`, `ID.jpeg`, `ID.png`, `ID.gif`, bez względu na wielkość rozszerzenia. Nie przegląda podfolderów ani dowiązań. Maksymalnie 5000 wpisów w folderze, 4 MB i 16 megapikseli na zdjęcie.

`POST /api/admin/cliques/photos/import` przyjmuje `{ "folder": "C:\\Zdjęcia\\Kliki" }`; wymaga sesji administratora i CSRF. Administrator może przypisać zdjęcia do wszystkich istniejących klik, w tym importowanych i cudzych, bez zmiany ich właściciela. Nie przyjmuje danych kont ani roli z żądania.

Każde ID musi mieć dokładnie jeden plik. Nieznane ID, niepasujące nazwy i kliki z przypisanym zdjęciem są pomijane. Kilka plików dla jednego ID jest raportowane jako błąd bez wybierania losowego pliku. Wynik zawiera liczniki `imported`, `skipped`, `failed` oraz listę `items` z nazwą pliku, ID, statusem i komunikatem. Nie zwraca źródłowych ścieżek ani szczegółów wyjątków.

Oryginały pozostają bez zmian. Backend dekoduje zdjęcie, usuwa metadane i zapisuje JPEG pod nazwą UUID, z zachowaniem proporcji i maksymalnym bokiem 1600 px. Katalog docelowy: `backend/data/photos/cliques/`, konfigurowany przez `app.photos.cliques-directory`. W bazie powstaje `Photo` typu `CLIQUE`, powiązane przez `cliques.photo_id`. Każdy plik ma osobną transakcję; błędny plik nie wycofuje poprawnych, a niezatwierdzony zapis sprząta nowy plik. Blokada kliki serializuje równoległe importy. Ponowienie nie tworzy duplikatów dla klik z przypisanym zdjęciem. Import nie zastępuje istniejących zdjęć.

Zdjęcia są serwowane wyłącznie przez uwierzytelniony `GET /api/cliques/{id}/photo`, z dotychczasową własnością kliki i kontrolą bezpiecznej ścieżki. Nie są publicznymi zasobami statycznymi. Odczyt cudzej kliki nadal daje 404, także administratorowi; uprawnienie importu nie poszerza odczytu klik.
