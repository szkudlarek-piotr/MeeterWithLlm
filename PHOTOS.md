# Zdjęcia i profilówki

## Ikony kategorii miejsc

Grafiki PNG kategorii są w `backend/data/photos/placeCategories/` (konfiguracja `app.photos.place-categories-directory`). Zalogowani odczytują je przez `GET /api/places/category-icons/{filename}`; endpoint akceptuje tylko proste nazwy PNG i pliki wewnątrz tego katalogu. Nie udostępnia innych katalogów zdjęć. Przy przenoszeniu aplikacji zachowaj ten folder obok pozostałych plików runtime.

`src/PlacesCategoriesDict.js` przypisuje kategorię do pliku. Wspólny TripMap używa go na mapie osoby i przy podróżach (również w kalendarzu). Dopasowanie pomija wielkość liter i nadmiarowe spacje. Ikony mają jasne tło dla czytelności w obu motywach. Nieznana/pusta kategoria lub brak pliku zachowuje zwykły punkt; nazwy miejsc pozostają w podpowiedziach. Punkty `/api/people/browse/{id}/map` zawierają kategorię źródłowego miejsca.

## Model zgodny z migracją

`org.example.meeter.multimedia.photo.Photo` odwzorowuje tabelę `photos`: `id`, `photo_name`, `latitude`, `longitude`, `photo_type`, `generation_time`, `uuid`. Typ jest tekstową nazwą enuma: `PROFILE_PICTURE`, `EVENT`, `TRIP`, `SUPERPOWER`. Model celowo nie wymaga dodatkowego właściciela ani nowego czasu utworzenia dla importowanych metadanych. Nie ma publicznego endpointu do odczytu całej tabeli zdjęć.

`Human.profilePhoto` to relacja jeden-do-jednego przez `people.profile_photo_id`, zgodna z oryginałem. Podczas migracji zachowaj identyfikatory zdjęć i odwołania osób; po imporcie ustaw licznik ID powyżej maksimum importowanych ID zgodnie z używaną bazą. Kopiuj pliki wskazane przez `photos.photo_name` do katalogu `app.photos.directory` (domyślnie `backend/data/photos/people/`). Nazwa oznacza pojedynczy plik, bez ścieżki ani URL. PNG, JPEG i GIF są serwowane z typem wynikającym z zawartości. Brak pliku, niewłaściwy typ zdjęcia, niebezpieczna nazwa lub dowiązanie prowadzące poza katalog daje 404. Nie umieszczaj plików w publicznych zasobach statycznych.

DTO nadal udostępnia `profilePhotoUrl` jako `/api/people/{id}/photo?v={uuid zdjęcia}`. Dotychczasowy frontend używa tego pola również dla uczestników i awatarów. Odczyt wymaga własności osoby; cytaty nadal pomijają portrety cudzych osób. Gdy relacja jest pusta, pozostaje obsługa starszego `profile_photo_file`. Nowe importy profilówek zapisują również rekord `Photo` typu `PROFILE_PICTURE`. Usuwanie osoby ani zmiana profilówki nie usuwa kaskadowo metadanych zdjęcia.

Wszystkie cztery typy można przechowywać w tabeli `photos`. Sam typ nie dodaje galerii ani powiązań do podróży i supermocy; oryginalne `Event` i `Citybreak` nie miały relacji do tej tabeli. Obecny mechanizm obrazów wydarzeń opisany poniżej pozostaje niezależny. Migracja rekordów i kopiowanie plików nie uruchamiają się automatycznie przy starcie aplikacji.

## Zdjęcia wydarzeń

`POST /api/events` zapisuje wydarzenie, towarzystwo i opcjonalne zdjęcie w ramach jednej transakcji. `placeString` pozwala dopisać opis miejsca niezależnie od wybranego `placeId`. `photoName` to opcjonalna etykieta do 255 znaków, a nie nazwa pliku na dysku. Dostępne jest najwyżej jedno źródło zdjęcia:

- `photoUrl`: bezpośredni publiczny link, pobierany przez ten sam zabezpieczony klient HTTP co profilówki.
- `photoData`: zawartość pliku zakodowana w Base64, bez prefiksu data URL; oryginalny plik do 4 MB.
- `photoEventId`: ID wcześniejszego własnego eventu ze zdjęciem. Współdzielona jest lokalna kopia pliku; nie jest pobierana ponownie.

Bez źródła event jest zapisywany bez zdjęcia. Obrazy są dekodowane (do 16 megapikseli), pozbawiane metadanych i zapisywane jako JPEG z zachowaniem proporcji, z dłuższym bokiem do 1600 px. Katalog: `backend/data/photos/events/`, konfigurowany przez `app.photos.events-directory`. Nazwy plików to losowe UUID; etykieta użytkownika nie wpływa na ścieżkę. Wycofanie transakcji usuwa nowo zapisany plik.

DTO eventu zawiera `details.photoUrl` (lokalny adres lub null), `details.photoName` i `details.placeString`. `GET /api/events/{id}/photo` wymaga zalogowania oraz własności eventu. Formularz pozwala wyszukiwać wcześniejsze wydarzenia po nazwie i przeglądać strony ich zdjęć. Żadna z tych kopii nie jest umieszczana w `resources/static`.

## Zdjęcia osób

Modal profilu umożliwia właścicielowi osoby lub administratorowi dodanie lub zmianę zdjęcia z pliku albo publicznego linku, także dla osoby zaimportowanej bez właściciela lub należącej do innego konta. `POST /api/people/{id}/photo` przyjmuje dokładnie jedno niepuste pole: `url` lub `photoData` (Base64 bez prefiksu data URL). Limity pliku i normalizacja pozostają wspólne: 4 MB, 16 megapikseli, JPEG 512×512. `GET /api/people/browse/{id}` zwraca `canEditPhoto`, wyliczane z własności osoby lub roli administratora. Wyjątek administratora dotyczy tylko zapisu zdjęcia; nie zmienia właściciela osoby ani zasad zwykłego odczytu zdjęć. Dla cudzej osoby odpowiedź zapisu wskazuje katalogowy adres portretu. Nowe zdjęcie aktualizuje też awatar karty bez odświeżania strony.

Formularz osoby przyjmuje opcjonalne `profilePhotoUrl`: bezpośredni publiczny link HTTP/HTTPS do obrazu. Backend pobiera plik (do 4 MB, do 20 sekund), sprawdza dekoderem jego zawartość i wymiary (do 16 megapikseli), a następnie zapisuje wykadrowany JPEG 512×512 bez metadanych.

Pliki trafiają do `backend/data/photos/people/`. Nazwa ma postać `<uuid-osoby>-<uuid-zdjęcia>.jpg`, więc polskie litery, duplikaty nazwisk i nazwy zdalnych plików nie powodują kolizji. Oryginalny link nie jest używany do wyświetlania zdjęcia ani przechowywany przy osobie.

- `POST /api/people` — opcjonalne `profilePhotoUrl` obok dotychczasowych pól.
- `POST /api/people/{id}/photo` — zmiana profilówki, JSON `{ "url": "https://example.org/photo.jpg" }`.
- `GET /api/people/{id}/photo` — własna lokalna kopia JPEG; wymaga zalogowania i prawa do osoby.
- DTO osób oraz uczestników interakcji zawierają `profilePhotoUrl` wskazujące na lokalny endpoint, a nie zdalną witrynę.

Zmiana zdjęcia usuwa poprzedni plik dopiero po zatwierdzeniu transakcji. Nieudany import zachowuje poprzednią profilówkę; wycofanie zapisu usuwa nowy plik. Jeżeli import przy dodawaniu osoby się nie powiedzie, osoba nie jest zapisywana częściowo.

Pobieranie nie przekazuje zdalnym serwerom cookie ani danych konta. Dozwolone są standardowe porty 80 i 443. Adresy lokalne, prywatne, multicast, link-local, usługi metadanych i niedozwolone adresy IPv6 są blokowane, także po przekierowaniu. DNS jest sprawdzany w resolverze używanym bezpośrednio przez klienta HTTP. Pobranie nie może przejść z HTTPS na HTTP. SVG i inne pliki nieobsługiwane przez dekoder obrazu nie są zapisywane.

Na Windows uruchom `./start.ps1` w folderze backendu. Skrypt używa zaufanego systemowego magazynu certyfikatów, nie wyłącza sprawdzania TLS.

## Historyczne dane przykładowe

Generator przykładowych danych i pobieranie demonstracyjnych portretów zostały usunięte. Dawniej utworzone osoby mogą nadal mieć znacznik demo=true oraz lokalne zdjęcia. Usunięcie generatora nie kasuje istniejących rekordów ani plików.

## Profilówki we wspólnej liście ludzi

DTO uczestników interakcji używa zwykłego adresu portretu dla własnych osób. Dla osób zaimportowanych bez właściciela lub należących do innego konta wskazuje istniejący endpoint katalogu `/api/people/browse/{id}/photo`, z zachowaniem parametru wersji zdjęcia. Osoby wykluczone z katalogu przez `my_exes`, których konto nie posiada, nie otrzymują adresu portretu. Dzięki temu kalendarz wyświetla dostępne portrety importowanych uczestników bez zmiany uprawnień endpointów zdjęć.

Na wyraźne życzenie użytkownika wspólny katalog osób pokazuje wszystkie rekordy Human po zalogowaniu. Jego DTO wskazuje na GET /api/people/browse/{id}/photo, który udostępnia profilówki także osób bez właściciela i przypisanych innym kontom. Zachowuje walidację typu zdjęcia, pliku i ścieżki. Dotychczasowy GET /api/people/{id}/photo, selektory osób i zasady widoczności portretów w cytatach pozostają bez zmian. Zapis profilówki ma opisany wyżej wyjątek administratora.
