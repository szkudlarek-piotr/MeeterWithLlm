import { useEffect, useState } from 'react'
import { api } from './api.js'
import { PhotoForm } from './PersonProfile.jsx'
import PersonAvatar from './PersonAvatar.jsx'

export default function SelfProfile({ onChange }) {
  const [person, setPerson] = useState(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    setError('')
    api('/people/self', { signal: controller.signal }).then(setPerson)
      .catch(failure => { if (!controller.signal.aborted) setError(failure.message) })
    return () => controller.abort()
  }, [retry])
  function saved(value) { setPerson(value); onChange() }
  async function submit(event) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const body = Object.fromEntries(['name', 'surname', 'birthdayDate', 'fbLink', 'gender', 'livesIn']
      .map(key => [key, String(data.get(key)).trim() || null]))
    setBusy(true); setError(''); setNotice('')
    try { saved(await api('/people/self', { method: 'PUT', body })); setNotice('Zapisano Twoje dane.') }
    catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }
  return <>
    <h2 id="self-profile-title">Moje dane</h2>
    {error && <p role="alert" className="field-error">{error} {!person && <button onClick={() => setRetry(value => value + 1)}>Spróbuj ponownie</button>}</p>}
    {!person && !error && <p role="status">Wczytywanie profilu…</p>}
    {person && <>
      <div className="person-detail-heading"><PersonAvatar person={person} /><p>{person.fullName}</p></div>
      <form className="human-form" onSubmit={submit}>
        <fieldset disabled={busy}><legend>Dane osobowe</legend><div className="form-grid">
          <label>Imię<input name="name" defaultValue={person.name} required maxLength={100} pattern=".*\S.*" /></label>
          <label>Nazwisko<input name="surname" defaultValue={person.surname || ''} maxLength={100} /></label>
          <label>Data urodzenia<input name="birthdayDate" type="date" defaultValue={person.birthdayDate || ''} max={new Date().toLocaleDateString('sv-SE')} /></label>
          <label>Płeć<input name="gender" defaultValue={person.gender || ''} maxLength={1} /><small className="field-hint">Opcjonalny kod płci (1 znak).</small></label>
          <label className="full-width">Mieszka w<input name="livesIn" defaultValue={person.livesIn || ''} maxLength={200} /></label>
          <label className="full-width">Link do Facebooka<input name="fbLink" type="url" defaultValue={person.fbLink || ''} maxLength={2048} /></label>
        </div><button className="primary submit" type="submit">{busy ? 'Zapisywanie…' : 'Zapisz dane'}</button></fieldset>
      </form>
      {notice && <p role="status">{notice}</p>}
      <PhotoForm person={person} endpoint="/people/self/photo" onSave={saved} />
    </>}
  </>
}
