# Tworzenie użytkowników w administracji

**Administracja → Dodaj użytkownika** pozwala powiązać nowe konto z istniejącą osobą Human. Wybierz osobę bez konta i podaj login. Osoby w podpowiedziach mają ID, aby rozróżnić identyczne nazwiska. Wyszukiwarka obejmuje także rekordy importowane, bez właściciela, cudze oraz my_exes, ale wyłącznie bez konta. Nie zmienia to zakresu innych selektorów.

- Uwierzytelniony `GET /api/admin/users/people?q=&limit=50` (limit 1–100) wyszukuje osoby po imieniu/nazwisku. Wykluczenie osób posiadających konto działa przed LIMIT. Wynik zawiera jedynie ID, nazwę i URL portretu przez istniejący endpoint uczestników. Nie ujawnia listy kont ani ich danych uwierzytelniających.
- `POST /api/admin/users` przyjmuje `{ humanId, username }`, wymaga ROLE_ADMIN i CSRF, zwraca 201 z `{ id, username, humanId, fullName }`. Nie zwraca hasła, hasha, tokenu ani encji User. Odpowiedzi mają `Cache-Control: no-store`.

Login jest obcinany na brzegach i zamieniany na małe litery tak samo jak przy logowaniu. Musi zaczynać się literą lub cyfrą i mieć 1–64 znaki; dalej dozwolone są także `.`, `_`, `@`, `+`, `-`. Zajęty login lub osoba z kontem: 409. Brak osoby: 404, nieprawidłowe dane: 400, brak roli: 403. Blokada osoby serializuje próby przypisania dwóch kont; unikalności w bazie chronią też równoległe zapisy. Nowy użytkownik ma wyłącznie ROLE_USER; pola roli, właściciela i hasła od klienta nie sterują tworzeniem konta.

Nie tworzymy nowej osoby i nie zmieniamy jej właściciela, interakcji, zdjęć ani innych danych. Nowe konto nie otrzymuje automatycznie własności rekordów osoby. Administrator pozostaje zalogowany na swoim koncie; nowe konto nie dostaje automatycznie sesji.

## Generowane hasła i passwords.csv

Backend generuje hasło z 24 losowych bajtów `SecureRandom`, kodowanych Base64 URL, z literowym prefiksem. Wynik mieści się w dotychczasowych limitach haseł i jest w bazie wyłącznie hashem BCrypt. Jawne hasło, na wyraźne życzenie użytkownika, jest dopisywane do lokalnego CSV:

`backend/data/passwords.csv` przy uruchamianiu backendu z folderu `backend`.

Konfiguracja: `app.admin.passwords-file` (domyślnie `./data/passwords.csv`). Nagłówek UTF-8 CSV: `username,password,human_id,created_at`. Każde nowe konto otrzymuje jeden wiersz; istniejące dane nie są nadpisywane. Pola są cytowane, a login i generator hasła wykluczają początki interpretowane jako formuły arkusza. Nie pokazujemy haseł w HTTP, UI ani logach. `passwords.csv` i katalog runtime `data/` są ignorowane przez Git. Plik jest lokalnym zapisem jawnych **haseł początkowych**, nie aktualnym rejestrem po późniejszych zmianach hasła. Normalna zmiana hasła i unieważnianie sesji pozostają bez zmian.

Zapis jest blokowany do końca transakcji; błąd pliku wycofuje nowe konto (503), a rollback bazy odtwarza poprzednią długość CSV. Plik z innym nagłówkiem lub dowiązanie nie są nadpisywane. Blokada JVM i pliku zabezpiecza równoległe dopisywanie. Baza i lokalny plik nie są wspólnym systemem transakcyjnym: po nagłym przerwaniu procesu pomiędzy zapisem pliku a commitem może pozostać wiersz dla niezatwierdzonego konta; przy takiej awarii sprawdź plik względem kont przed ponowieniem. Zwykłe błędy i wycofania są obsługiwane oraz testowane.

Tworzenie kont działa tylko na jawne żądanie administratora. Nie jest wykonywane przy starcie, migracji ani imporcie zdjęć. Zmiana nie wymaga nowej migracji schematu. Testy używają izolowanego H2 i tymczasowego pliku, nigdy runtime passwords.csv. Podczas implementacji nie utworzono kont ani haseł w bazie użytkownika.
