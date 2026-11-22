import { useState } from 'react'
import { api } from './api.js'
import EntityPicker from './EntityPicker.jsx'
import { t, tf } from './i18n.js'

export default function AdminUserForm() {
  const [person, setPerson] = useState([])
  const [username, setUsername] = useState('')
  const [version, setVersion] = useState(0)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [created, setCreated] = useState(null)
  async function submit(event) {
    event.preventDefault()
    setError(''); setCreated(null)
    if (!person.length) { setError(t('Wybierz osobę z listy.')); return }
    setBusy(true)
    try {
      setCreated(await api('/admin/users', { body: { humanId: person[0].id, username: username.trim() } }))
      setPerson([]); setUsername(''); setVersion(value => value + 1)
    } catch (error) { setError(error.message) } finally { setBusy(false) }
  }
  return <section className="card admin-photo-import" aria-labelledby="admin-user-title">
    <h3 id="admin-user-title">{t('Dodaj użytkownika')}</h3>
    <p>{t('Wybierz istniejącą osobę bez konta. Nowy użytkownik otrzyma zwykłe uprawnienia użytkownika.')}</p>
    <form onSubmit={submit}>
      <fieldset disabled={busy}>
        <EntityPicker key={version} label={t('Osoba')} source="admin/users/people" value={person} onChange={setPerson} required />
        <label htmlFor="admin-user-login">{t('Login')}</label>
        <input id="admin-user-login" value={username} onChange={event => setUsername(event.target.value)} required maxLength={64} autoComplete="off" autoCapitalize="none" spellCheck={false} aria-describedby="admin-login-help" />
        <p id="admin-login-help" className="field-hint">{t('Do 64 znaków, początek literą lub cyfrą. Dozwolone są litery, cyfry oraz . _ @ + −. Login zostanie zapisany małymi literami.')}</p>
        <p className="field-hint">{t('Losowe hasło zostanie wygenerowane automatycznie i zapisane wraz z loginem w pliku passwords.csv na serwerze.')}</p>
        <button className="primary" disabled={!person.length}>{busy ? t('Tworzenie konta…') : t('Utwórz konto')}</button>
      </fieldset>
    </form>
    {error && <p className="field-error" role="alert">{error}</p>}
    {created && <p role="status">{tf('Utworzono konto „{username}” dla osoby {fullName}. Hasło zapisano w passwords.csv.', { username: created.username, fullName: created.fullName })}</p>}
  </section>
}
