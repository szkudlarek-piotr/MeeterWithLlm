import { useEffect, useRef, useState } from 'react'
import Dashboard from './Dashboard.jsx'
import { api } from './api.js'

export default function App() {
  const [dark, setDark] = useState(() => {
    const saved = localStorage.getItem('meeter-theme')
    return saved ? saved === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches
  })
  const [user, setUser] = useState(null)
  const [checking, setChecking] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [mode, setMode] = useState('login')
  const dialog = useRef(null)

  useEffect(() => {
    const controller = new AbortController()
    api('/auth/me', { signal: controller.signal }).then(setUser).catch(() => {}).finally(() => {
      if (!controller.signal.aborted) setChecking(false)
    })
    const expired = () => { setUser(null); setMessage('Sesja wygasła. Zaloguj się ponownie.') }
    window.addEventListener('meeter:unauthorized', expired)
    return () => { controller.abort(); window.removeEventListener('meeter:unauthorized', expired) }
  }, [])

  useEffect(() => {
    document.documentElement.dataset.theme = dark ? 'dark' : 'light'
    localStorage.setItem('meeter-theme', dark ? 'dark' : 'light')
  }, [dark])

  function openDialog(nextMode) {
    setMode(nextMode)
    setError('')
    dialog.current.showModal()
  }

  async function login(event) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    setBusy(true)
    setError('')
    try {
      if (mode === 'password') {
        if (data.get('newPassword') !== data.get('confirmPassword')) throw new Error('Nowe hasła muszą być takie same.')
        await api('/auth/password', { body: { currentPassword: data.get('password'), newPassword: data.get('newPassword') } })
        setUser(null)
        setMessage('Hasło zmienione. Zaloguj się ponownie nowym hasłem.')
      } else {
        const account = await api('/auth/login', { body: { username: data.get('username'), password: data.get('password') } })
        setUser(account)
        setMessage('')
      }
      form.reset()
      dialog.current.close()
    } catch (error) { setError(error.message) } finally { setBusy(false) }
  }

  async function logout() {
    setBusy(true)
    try { await api('/auth/logout', { body: {} }); setUser(null); setMessage('Wylogowano pomyślnie.') }
    catch (error) { if (error.status === 401) setUser(null); setMessage(error.message) }
    finally { setBusy(false) }
  }

  return (
    <main className={user ? 'app-shell' : 'welcome'}>
      <header className="topbar">
      <a className="brand" href="/" aria-label="Meeter — strona główna">
        <span className="brand-mark">m</span> meeter<span className="dot">.</span>
      </a>
        <nav className="actions" aria-label="Ustawienia i konto">
          <button className="theme-toggle" onClick={() => setDark(!dark)} aria-pressed={dark} aria-label="Tryb ciemny">
            <span aria-hidden="true">{dark ? '☀' : '☾'}</span>
            <span>{dark ? 'Tryb jasny' : 'Tryb ciemny'}</span>
          </button>
          {user ? (
            <><button className="text-button account-password" onClick={() => openDialog('password')}>Zmień hasło</button><button className="primary" disabled={busy} onClick={logout}>Wyloguj się</button></>
          ) : (
            <button className="primary" disabled={checking} onClick={() => openDialog('login')}>{checking ? 'Sprawdzanie sesji…' : 'Zaloguj się'} <span aria-hidden="true">↗</span></button>
          )}
        </nav>
      </header>
      {message && <p className="session-message" role="status">{message}</p>}
      {user ? <Dashboard key={user.id} email={user.username} actorId={user.humanId} /> : <>
      <section className="intro">
        <p className="eyebrow">LUDZIE · SPOTKANIA · WSPOMNIENIA</p>
        <h1>Wszystko zaczyna się<br />od <span>spotkania.</span></h1>
        <p className="description">
          Twoje miejsce na ludzi, wspólne chwile i historie, do których warto wracać.
        </p>
        <div className="status"><span /> Nowy rozdział Meetera. Zaczynamy.</div>
      </section>
      <footer>Małe chwile. Wielkie znaczenie.</footer>
      </>}
      <dialog ref={dialog} className="login-dialog" aria-labelledby="login-title" onClick={event => {
        if (event.target === dialog.current) {
          const bounds = dialog.current.getBoundingClientRect()
          if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) dialog.current.close()
        }
      }}>
        <button className="close-dialog" aria-label="Zamknij logowanie" onClick={() => dialog.current.close()}>×</button>
        <p className="eyebrow">DOBRZE CIĘ WIDZIEĆ</p>
        <h2 id="login-title">{mode === 'password' ? 'Zmień swoje hasło.' : 'Wróć do swoich historii.'}</h2>
        <p className="login-description">{mode === 'password' ? 'Zmiana hasła wyloguje Cię ze wszystkich urządzeń.' : 'Zaloguj się do Meetera i bądź bliżej swoich ludzi.'}</p>
        <form key={mode} onSubmit={login}>
          {mode === 'login' && <><label htmlFor="username">Nazwa użytkownika</label><input autoFocus id="username" name="username" autoComplete="username" placeholder="pszkudlarek" maxLength={64} required /></>}
          <label htmlFor="password">{mode === 'password' ? 'Obecne hasło' : 'Hasło'}</label>
          <input id="password" name="password" type="password" autoComplete="current-password" placeholder="Twoje hasło" maxLength={72} required />
          {mode === 'password' && <><label htmlFor="new-password">Nowe hasło</label><input id="new-password" name="newPassword" type="password" autoComplete="new-password" minLength={12} maxLength={72} required /><label htmlFor="confirm-password">Powtórz nowe hasło</label><input id="confirm-password" name="confirmPassword" type="password" autoComplete="new-password" minLength={12} maxLength={72} required /><small className="field-hint">Minimum 12 znaków, maksymalnie 72 bajty UTF-8.</small></>}
          {error && <p className="field-error" role="alert">{error}</p>}
          <button className="primary submit" type="submit" disabled={busy}>{busy ? 'Proszę czekać…' : mode === 'password' ? 'Zmień hasło' : 'Zaloguj się →'}</button>
        </form>
      </dialog>
    </main>
  )
}
