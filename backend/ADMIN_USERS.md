# Creating users in administration

**Administration → Add user** links a new account to an existing Human person. Choose a person who has no account and enter a login. Suggestions show each person's ID to tell identical surnames apart. The search also covers imported records, unowned and foreign records, and `my_exes`, but only people without an account. Other pickers are unaffected.

- Authenticated `GET /api/admin/users/people?q=&limit=50` (limit 1–100) searches people by first or last name. Excluding people who already have an account happens before LIMIT. The result contains only ID, name and the portrait URL from the existing participant endpoint. It does not reveal the list of accounts or their credentials.
- `POST /api/admin/users` accepts `{ humanId, username }`, requires ROLE_ADMIN and CSRF, and returns 201 with `{ id, username, humanId, fullName }`. It never returns a password, hash, token or User entity. Responses carry `Cache-Control: no-store`.

The login is trimmed and lowercased, the same way as at sign-in. It must start with a letter or digit and be 1–64 characters long; `.`, `_`, `@`, `+` and `-` are also allowed after that. A taken login or a person who already has an account returns 409. A missing person returns 404, invalid data returns 400, and a missing role returns 403. A lock on the person serialises two attempts to assign accounts; database uniqueness constraints also protect against concurrent writes. A new user only ever gets ROLE_USER; role, owner and password fields sent by the client do not control account creation.

The endpoint does not create a new person and does not change a person's owner, interactions, photos or other data. A new account does not automatically receive ownership of the person's records. The administrator stays signed in to their own account; the new account does not get a session automatically.

## Generated passwords and passwords.csv

The backend generates a password from 24 random bytes from `SecureRandom`, encoded as Base64 URL, with a letter prefix. The result fits the existing password limits, and the database stores only a BCrypt hash. An explicit password, when the user asks for one, is appended to a local CSV file:

`backend/data/passwords.csv` when the backend is started from the `backend` folder.

Configuration: `app.admin.passwords-file` (default `./data/passwords.csv`). The UTF-8 CSV header is `username,password,human_id,created_at`. Each new account adds one row; existing data is never overwritten. Fields are quoted, and both the login and the password generator avoid starts that spreadsheets would interpret as formulas. Passwords are never shown over HTTP, in the UI or in logs. `passwords.csv` and the runtime `data/` folder are Git-ignored. The file is a local record of explicit **initial passwords**, not a current register after later password changes. Normal password changes and session invalidation are unchanged.

Writing is locked until the end of the transaction. A file error rolls back the new account (503), and a database rollback restores the previous CSV length. A file with a different header or a link is never overwritten. A JVM and file lock protects against concurrent appends. The database and the local file are not one transactional system: if the process is killed between the file write and the commit, a row for an uncommitted account may remain. After such a crash, check the file against the accounts before retrying. Ordinary errors and rollbacks are handled and tested.

Accounts are created only on an explicit administrator request. Creation does not run at startup, during migrations or during photo import. The change does not need a new schema migration. Tests use an isolated H2 database and a temporary file, never the runtime `passwords.csv`. No accounts or passwords were created in the user's database during implementation.
