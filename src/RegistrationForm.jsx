import { useState } from 'react'
import { api } from './api.js'

export default function RegistrationForm({ verificationToken, onRegistered }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(event) {
    event.preventDefault()
    const { token, username, email, password, confirmation } = Object.fromEntries(new FormData(event.currentTarget))
    if (password !== confirmation) return setError('Hasła muszą być takie same.')
    if (new TextEncoder().encode(password).length > 72) return setError('Hasło może mieć maksymalnie 72 bajty UTF-8.')
    setBusy(true)
    setError('')
    try {
      await api('/auth/register', { body: { token: token.trim(), username: username.trim(), email: email.trim(), password } })
      onRegistered('Sprawdź pocztę i potwierdź adres e-mail, aby zakończyć rejestrację.')
    } catch (error) {
      setError(error.message)
    } finally {
      setBusy(false)
    }
  }

  async function verify() {
    setBusy(true)
    setError('')
    try {
      await api('/auth/verify-email', { body: { token: verificationToken } })
      onRegistered('Adres e-mail potwierdzony. Możesz się zalogować.')
    } catch (error) {
      setError(error.message)
    } finally {
      setBusy(false)
    }
  }

  if (verificationToken) return <>
    {error && <p className="field-error" role="alert">{error}</p>}
    <button type="button" className="primary submit" disabled={busy} onClick={verify}>Potwierdź adres e-mail</button>
  </>

  return <form onSubmit={submit}>
    <label htmlFor="register-token">Token zaproszenia</label>
    <input autoFocus id="register-token" name="token" autoComplete="off" spellCheck={false} maxLength={512} required />
    <label htmlFor="register-username">Nazwa użytkownika</label>
    <input id="register-username" name="username" autoComplete="username" maxLength={64} required />
    <label htmlFor="register-email">Adres e-mail</label>
    <input id="register-email" name="email" type="email" autoComplete="email" maxLength={254} required />
    <label htmlFor="activate-password">Hasło</label>
    <input id="activate-password" name="password" type="password" autoComplete="new-password" minLength={12} maxLength={72} aria-describedby="activate-password-hint" required />
    <small id="activate-password-hint" className="field-hint">Minimum 12 znaków, maksymalnie 72 bajty UTF-8.</small>
    <label htmlFor="activate-confirmation">Powtórz hasło</label>
    <input id="activate-confirmation" name="confirmation" type="password" autoComplete="new-password" minLength={12} maxLength={72} required />
    {error && <p className="field-error" role="alert">{error}</p>}
    <button className="primary submit" type="submit" disabled={busy}>{busy ? 'Proszę czekać…' : 'Załóż konto'}</button>
  </form>
}
