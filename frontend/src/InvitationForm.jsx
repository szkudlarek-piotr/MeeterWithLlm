import { useState } from 'react'
import { api } from './api.js'
import EntityPicker from './EntityPicker.jsx'
import { t } from './i18n.js'

export default function InvitationForm() {
  const [person, setPerson] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [token, setToken] = useState('')

  async function submit(event) {
    event.preventDefault()
    if (!person.length) return
    setBusy(true)
    setError('')
    try {
      const invitation = await api(`/auth/invitations/${person[0].id}`, { body: {} })
      setToken(invitation.token)
    } catch (error) {
      setError(error.message)
    } finally {
      setBusy(false)
    }
  }

  if (token) return <>
    <p role="status">{t('Przekaż token wybranej osobie. Może go użyć w formularzu rejestracji przez 24 godziny.')}</p>
    <label htmlFor="invitation-token">{t('Token zaproszenia')}</label>
    <input id="invitation-token" value={token} readOnly onFocus={event => event.target.select()} />
  </>

  return <form onSubmit={submit}>
    <fieldset disabled={busy}>
      <EntityPicker label={t('Osoba')} source="people" value={person} onChange={setPerson} required />
      {error && <p className="field-error" role="alert">{error}</p>}
      <button className="primary submit" type="submit" disabled={!person.length}>{busy ? t('Generowanie…') : t('Wygeneruj zaproszenie')}</button>
    </fieldset>
  </form>
}
