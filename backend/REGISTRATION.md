# Registration with an invitation

After signing in, choose **Invite a person** and select a person you have added who does not have an account. Copy the generated JWT token and give it to that person. The recipient opens **Sign up** and enters the token, a login, an e-mail address and a password. The backend sends a link to the given address. After opening the link, choose **Confirm e-mail address**. Only then is the account created, with the `ROLE_USER` role and linked to the existing profile. The invitation and the link expire 24 hours after the JWT was generated.

Set the environment variable `MEETER_INVITATION_JWT_SECRET` for the backend process. Use a random secret of at least 32 bytes. Changing the key invalidates JWTs issued earlier. For e-mail, set `GMAIL_SMTP_USER` (the sender address), `GMAIL_APP_PASSWORD` (a Gmail app password) and `MEETER_PUBLIC_URL` (the frontend address; locally, for example `http://127.0.0.1:5173`). With a local address, the link must be opened on the same computer.

If you run the backend locally from PowerShell, the commands below save a new key for the current user and pass it to processes started from that window. Run them only during the first setup, then restart the backend.

```powershell
$invitationKeyBytes = New-Object byte[] 32
$invitationRandom = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$invitationRandom.GetBytes($invitationKeyBytes)
$invitationRandom.Dispose()
$env:MEETER_INVITATION_JWT_SECRET = [Convert]::ToBase64String($invitationKeyBytes)
[Environment]::SetEnvironmentVariable('MEETER_INVITATION_JWT_SECRET', $env:MEETER_INVITATION_JWT_SECRET, 'User')
```

`POST /api/v2/auth/invitations/{humanId}` requires a session, CSRF and the UUID of a person created by the inviting user. `POST /api/v2/auth/register` requires CSRF and a valid JWT from the invitation. `POST /api/v2/auth/verify-email` requires CSRF and the one-time token from the message. Migration V12 adds the fields for storing a pending request, including a hash of the token and a BCrypt hash of the password.

Migration V13 adds the table `account_invitation_history`. Confirming the e-mail records there the IDs of the inviting user, the created account and the invited person, and the time the invitation was accepted. The history covers completed registrations from the deployment of V13 onwards, and keeps the IDs even after the account or the person is deleted.

Flyway migration V11 ensures that the column `people.created_by_id` exists and invalidates earlier invitations. New people receive the ID of the creator who is signed in.

Assign the existing people to user 1 once, manually, with [backfill_people_creator.sql](../backfill_people_creator.sql) after V11. Check that `app_users.id = 1` points to the correct account, and make a backup as described in [MIGRATIONS.md](MIGRATIONS.md). The script overwrites the existing creators, so run it before writes in the application are resumed.
