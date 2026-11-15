# Rejestracja z zaproszeniem

Po zalogowaniu wybierz **Zaproś osobę** i wskaż dodaną przez siebie osobę bez konta. Skopiuj wygenerowany token JWT i przekaż go tej osobie. Odbiorca otwiera **Zarejestruj się** i podaje token, login, e-mail oraz hasło. Backend wysyła link na podany adres. Po otwarciu linku wybierz **Potwierdź adres e-mail**. Dopiero wtedy powstaje konto z rolą `ROLE_USER`, powiązane z istniejącym profilem. Zaproszenie i link wygasają 24 godziny od wygenerowania JWT.

Ustaw zmienną środowiskową `MEETER_INVITATION_JWT_SECRET` dla procesu backendu. Użyj losowego sekretu zawierającego co najmniej 32 bajty. Zmiana klucza unieważnia wcześniej wydane JWT. Dla poczty ustaw `GMAIL_SMTP_USER` (adres nadawcy), `GMAIL_APP_PASSWORD` (hasło aplikacji Gmail) i `MEETER_PUBLIC_URL` (adres frontendu, lokalnie np. `http://127.0.0.1:5173`). Przy lokalnym adresie link trzeba otworzyć na tym samym komputerze.

Jeśli uruchamiasz backend lokalnie w PowerShellu, poniższe polecenia zapiszą nowy klucz dla bieżącego użytkownika i przekażą go procesom uruchamianym z tego okna. Wykonaj je tylko przy pierwszej konfiguracji, a następnie uruchom backend ponownie.

```powershell
$invitationKeyBytes = New-Object byte[] 32
$invitationRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$invitationRandom.GetBytes($invitationKeyBytes)
$invitationRandom.Dispose()
$env:MEETER_INVITATION_JWT_SECRET = [Convert]::ToBase64String($invitationKeyBytes)
[Environment]::SetEnvironmentVariable('MEETER_INVITATION_JWT_SECRET', $env:MEETER_INVITATION_JWT_SECRET, 'User')
```

`POST /api/v2/auth/invitations/{humanId}` wymaga sesji, CSRF i UUID osoby utworzonej przez zapraszającego. `POST /api/v2/auth/register` wymaga CSRF oraz poprawnego JWT z zaproszenia. `POST /api/v2/auth/verify-email` wymaga CSRF i jednorazowego tokenu z wiadomości. Migracja V12 dodaje pola do przechowania oczekującego zgłoszenia, w tym skrótu tokenu oraz skrótu hasła BCrypt.

Migracja V13 dodaje tabelę `account_invitation_history`. Potwierdzenie e-maila zapisuje w niej identyfikator zapraszającego, utworzonego konta i zaproszonej osoby oraz czas zaakceptowania zaproszenia. Historia obejmuje ukończone rejestracje od wdrożenia V13 i zachowuje identyfikatory również po usunięciu konta lub osoby.

Migracja Flyway V11 zapewnia obecność kolumny `people.created_by_id` i unieważnia wcześniejsze zaproszenia. Nowe osoby otrzymują ID zalogowanego twórcy.

Jednorazowe przypisanie istniejących osób do użytkownika 1 wykonaj ręcznie przez [backfill_people_creator.sql](../backfill_people_creator.sql) po V11. Sprawdź, czy `app_users.id = 1` wskazuje właściwe konto, i wykonaj kopię zgodnie z [MIGRATIONS.md](MIGRATIONS.md). Skrypt nadpisuje dotychczasowych twórców i należy go wykonać przed wznowieniem zapisów w aplikacji.
