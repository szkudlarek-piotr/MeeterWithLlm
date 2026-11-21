# Review and adaptation of sign-in

Source: [Meeter authentication module](https://github.com/szkudlarek-piotr/Meeter_hibernate/tree/master/src/main/java/org/example/meeter/auth), reviewed on 18 October 2026. The assessment covers that module only, not the whole application or its infrastructure.

## What is good

- BCrypt for passwords.
- Tokens generated from 32 bytes of `SecureRandom`.
- `HttpOnly` cookie, `SameSite=Lax`, and `Secure` support.
- Token expiry **is checked** by a `TokenRepository` query, so the filter does not need to repeat the check.
- Separation of the user entity from the person entity.

## Changes in the new backend

| Observation in the source | New behaviour |
| --- | --- |
| The token is stored in the database in a form that could be used directly as a cookie | Only the SHA-256 hash of the token is stored in the database; the raw token exists only in the cookie |
| CSRF protection is disabled even though sign-in relies on automatically sent cookies | CSRF protection covers sign-in, sign-out and password change too |
| The module shown has no way to invalidate a token on logout | An explicit endpoint deletes the token from the database; replaying it after logout returns 401 |
| Any signed-in user can call the shown account and role creation endpoints | The first account is created by the local bootstrap; additional accounts can be created only by an administrator, who cannot grant the administrator role. See ADMIN_USERS.md |
| The user is accepted as a `Map<String,String>` without a validated contract | Validated input records and separate response DTOs |
| The module shown has no sign-in attempt limiting | A limit per login and address, with a generic message for a wrong password and for an unknown account |
| Duplicate password hashing in `UserService` and `PasswordEncoder` | A single BCrypt `PasswordEncoder` with cost 12 |
| The module shown has no password change and no invalidation of other sessions | A password change invalidates all tokens of the account |
| Form redirects to `/` | JSON responses 200/401/403/429 for React |

The link between an account and a person is one to one in the new model. `User` and tokens are never serialised directly. Every place, clique and person has an owner, and reading and assigning relations is limited to that owner's records. Trying to reference someone else's place or clique returns the same 404 as a record that does not exist.

The concept of tokens stored in the database and in a cookie comes from the source project; the implementation was adapted to the new API. Other data, passwords, configuration and the schema of the real database were not copied. JWT was not added: for a local application, revocable random tokens are sufficient.

## Persistent authentication history

Migration `V7__auth_event_history.sql` creates `auth_events`, independent of `auth_tokens`. It records `LOGIN_SUCCESS`, `LOGIN_FAILURE` (wrong password or unknown login), `LOGIN_THROTTLED`, explicit `LOGOUT` and `PASSWORD_CHANGED`. Each entry contains the server-side `occurred_at` (`Instant`), an optional `account_id`, the normalised login, an optional `session_record_id`, the connection IP and a User-Agent of up to 512 characters. For an unknown login or an attempt blocked by the limiter the account is null. The session record ID lets a successful sign-in be linked to its sign-out, but it is not a credential. Passwords, tokens, their hashes, cookies, request bodies and error details are never recorded.

The IP comes from `request.getRemoteAddr()`; the application does not trust `X-Forwarded-For` on its own. A reverse proxy requires deliberate configuration of trusted infrastructure. The User-Agent is untrusted, length-limited text; it does not prove a device's identity. Control characters in labels are replaced with spaces.

Failed sign-ins and rejections by the limiter are written by a separate bean with `REQUIRES_NEW`, so a rollback of the sign-in does not remove the event. Sign-in success, sign-out and password change write their audit entry atomically together with the session or password change. A failure to write the audit entry blocks the successful operation, rather than pretending the history is complete. The table has no foreign keys or cascades to accounts and tokens; it stores historical IDs and the login. Deleting a session, cleaning up expired tokens and deleting an account do not remove history. There is no automatic retention, no public endpoint and no history UI; operational access is through database permissions. Entries are not added for every authenticated request.

The scope covers attempts that reached credential checking or the limiter. Requests rejected earlier by CSRF or JSON validation, and natural token expiry, are not events of this register. Renewed sign-in and a password change still invalidate earlier sessions under the existing rules. Earlier events are not reconstructed from existing tokens. Before starting a version with V7, stop the backend and make a backup.

## Other limitations

Internal servlet dispatch to `ERROR` is allowed, so the original server error keeps status 500 instead of being replaced by 401 while `/error` is rendered. A direct HTTP request to `/error` is still forbidden; API endpoints require authentication. The frontend treats 401 as loss of session, so application errors must not be masked with that status. The error response body still contains no stack trace or exception message.

This is still a local development backend: there is no password recovery, no 2FA, no full account management and no shared limiter. H2 and automatic schema updates do not replace a production database and migrations. The initial password file must be deleted after the password is changed. A public deployment requires HTTPS and the `Secure` cookie flag.
