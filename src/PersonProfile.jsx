import { useEffect, useState } from 'react'
import { api } from './api.js'
import { PersonAvatar } from './CalendarPanel.jsx'
import QuoteContent from './QuoteContent.jsx'
import TripMap from './TripMap.jsx'

const sections = [['profile', 'Profil'], ['map', 'Mapa interakcji'], ['VISIT', 'Wizyty'], ['MEETING', 'Spotkania'], ['EVENT', 'Eventy'], ['TRIP', 'Podróże'], ['quotes', 'Cytaty']]
const date = value => new Date(`${value.slice(0, 10)}T12:00:00`).toLocaleDateString('pl-PL')

function PhotoForm({ person, onSave }) {
  const [source, setSource] = useState('file')
  const [url, setUrl] = useState('')
  const [file, setFile] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('')
    try {
      let body = { url }
      if (source === 'file') {
        if (!file) throw new Error('Wybierz zdjęcie.')
        if (file.size > 4 * 1024 * 1024) throw new Error('Zdjęcie może mieć maksymalnie 4 MB.')
        const data = await new Promise((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = () => resolve(String(reader.result).split(',')[1])
          reader.onerror = () => reject(new Error('Nie udało się odczytać pliku.'))
          reader.readAsDataURL(file)
        })
        body = { photoData: data }
      }
      const saved = await api(`/people/${person.id}/photo`, { body })
      onSave({ ...saved, profilePhotoUrl: saved.profilePhotoUrl?.replace(/^\/api\/people\/(?!browse\/)/, '/api/people/browse/') })
      setNotice('Zapisano zdjęcie profilowe.')
    } catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }
  return <form className="profile-photo-form" onSubmit={submit}>
    <fieldset disabled={busy}><legend>Dodaj lub zmień zdjęcie profilowe</legend>
      <label>Źródło zdjęcia<select value={source} onChange={event => { setSource(event.target.value); setError(''); setNotice('') }}><option value="file">Plik z komputera</option><option value="url">Publiczny link</option></select></label>
      {source === 'file' ? <label>Zdjęcie JPG, PNG lub GIF (do 4 MB)<input type="file" accept="image/jpeg,image/png,image/gif" required onChange={event => { setFile(event.target.files[0] || null); setNotice('') }} /></label> : <label>Bezpośredni adres zdjęcia<input type="url" required maxLength={2048} value={url} onChange={event => setUrl(event.target.value)} /></label>}
      <button className="primary" disabled={busy}>{busy ? 'Zapisywanie…' : 'Zapisz zdjęcie'}</button>
    </fieldset>
    {error && <p className="field-error" role="alert">{error}</p>}{notice && <p role="status">{notice}</p>}
  </form>
}

export default function PersonProfile({ entry, onPersonChange }) {
  const [person, setPerson] = useState(entry.person)
  const [profile, setProfile] = useState(null)
  const [section, setSection] = useState('profile')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const [profileError, setProfileError] = useState('')
  const requestKey = `${person.id}:${section}:${page}:${retry}`
  const data = result?.key === requestKey ? result.data : null
  useEffect(() => {
    const controller = new AbortController()
    setProfileError('')
    api(`/people/browse/${person.id}`, { signal: controller.signal }).then(value => { if (!controller.signal.aborted) { setProfile(value); setPerson(value.person) } })
      .catch(failure => { if (!controller.signal.aborted) setProfileError(failure.message) })
    return () => controller.abort()
  }, [person.id, retry])
  useEffect(() => {
    setResult(null); setError('')
    if (section === 'profile') return
    const controller = new AbortController()
    setLoading(true)
    const path = section === 'map' ? 'map' : section === 'quotes' ? `quotes?page=${page}` : `interactions?type=${section}&page=${page}`
    api(`/people/browse/${person.id}/${path}`, { signal: controller.signal }).then(value => {
      if (!controller.signal.aborted) setResult({ key: requestKey, data: value })
    })
      .catch(failure => { if (!controller.signal.aborted) setError(failure.message) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [person.id, section, page, retry, requestKey])
  return <>
    <div className="person-detail-heading"><PersonAvatar person={person} /><h2 id="person-detail-title">{person.fullName}</h2></div>
    <nav className="person-profile-tabs" aria-label="Szczegóły osoby">{sections.map(([key, label]) => <button key={key} type="button" aria-current={section === key ? 'page' : undefined} onClick={() => { setSection(key); setPage(0) }}>{label}</button>)}</nav>
    {profileError && <p role="alert" className="field-error">{profileError} <button onClick={() => setRetry(value => value + 1)}>Spróbuj ponownie</button></p>}
    {section === 'profile' ? <>
      <dl className="person-detail-fields">
        <div><dt>Twoje wspólne wizyty</dt><dd>{entry.visitCount}</dd></div>
        {person.birthdayDate && <div><dt>Data urodzenia</dt><dd>{date(person.birthdayDate)}</dd></div>}
        {person.livesIn && <div><dt>Mieszka w</dt><dd>{person.livesIn}</dd></div>}
        {person.home && <div><dt>Dom</dt><dd>{person.home.name}</dd></div>}
        {person.clique && <div><dt>Klika</dt><dd>{person.clique.cliqueName}</dd></div>}
      </dl>
      {/^(https?:\/\/)/i.test(person.fbLink || '') && <a href={person.fbLink} target="_blank" rel="noopener noreferrer">Profil na Facebooku ↗</a>}
      {profile?.canEditPhoto && <PhotoForm person={person} onSave={saved => { setPerson(saved); onPersonChange(saved) }} />}
      {profile && !profile.canEditPhoto && <p className="field-hint">Zdjęcie może zmienić właściciel rekordu osoby lub administrator.</p>}
      {!profile && !profileError && <p role="status" className="field-hint">Sprawdzanie uprawnień do zmiany zdjęcia…</p>}
    </> : <>
      {loading && <p role="status">Wczytywanie…</p>}
      {error && <p className="field-error" role="alert">{error} <button onClick={() => setRetry(value => value + 1)}>Spróbuj ponownie</button></p>}
      {!loading && !error && data && (section === 'map' ? <><p className="field-hint">Mapa wszystkich dostępnych wizyt, spotkań, eventów i podróży. Pokazujemy wyłącznie zapisane miejsca ze współrzędnymi.</p><TripMap places={data} title="Mapa interakcji" emptyMessage="Brak dostępnych interakcji z miejscami na mapie." /></> : <>
        <p role="status">Liczba wyników: {data.totalElements}</p>
        {!data.content.length && <p>Brak dostępnych {section === 'quotes' ? 'cytatów' : 'interakcji'}.</p>}
        {data.content.map(item => section === 'quotes' ? <article className="card profile-entry person-public-quote" key={item.id}><small>{item.isPublic ? 'PUBLICZNY CYTAT' : 'PRYWATNY CYTAT'}</small><QuoteContent quote={item.quote} tabIndex={0} /></article> : <article className="card profile-entry" key={item.id}>
          <h3>{item.name}</h3><p>{date(item.startDate)}{item.endDate && item.endDate.slice(0, 10) !== item.startDate.slice(0, 10) ? ` — ${date(item.endDate)}` : ''}</p>
          {(item.place || item.details.placeString) && <p>{[item.place?.name, item.details.placeString].filter(Boolean).join(' · ')}</p>}
          {item.longDesc && <p className="memory-description">{item.longDesc}</p>}
          <div className="calendar-detail-people">{item.participants.map(member => <span className="calendar-person" key={member.id}><PersonAvatar person={member} /><span>{member.fullName}</span></span>)}</div>
          {item.type === 'TRIP' && <TripMap places={item.details.places} />}
          {item.details.photoUrl && <img className="memory-photo" src={item.details.photoUrl} alt={item.details.photoName || item.name} loading="lazy" />}
        </article>)}
        {(page > 0 || data.hasNext) && <div className="event-photo-pages"><button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Poprzednia strona</button><span>Strona {page + 1}</span><button disabled={!data.hasNext} onClick={() => setPage(value => value + 1)}>Następna strona</button></div>}
      </>)}
    </>}
  </>
}
