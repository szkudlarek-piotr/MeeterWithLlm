import { useEffect, useState } from 'react'
import { api } from './api.js'
import { PersonAvatar, ParticipantsEditor } from './CalendarPanel.jsx'
import { interactionEntry } from './InteractionForm.jsx'
import QuoteContent from './QuoteContent.jsx'
import TripMap from './TripMap.jsx'
import { t } from './i18n.js'

const sections = [['profile', 'Profil'], ['map', 'Mapa interakcji'], ['VISIT', 'Wizyty'], ['MEETING', 'Spotkania'], ['EVENT', 'Eventy'], ['TRIP', 'Podróże'], ['quotes', 'Cytaty']]
const date = value => new Date(`${value.slice(0, 10)}T12:00:00`).toLocaleDateString('pl-PL')

export function PhotoForm({ person, onSave, endpoint = `/people/${person.id}/photo` }) {
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
      const saved = await api(endpoint, { body })
      onSave({ ...saved, profilePhotoUrl: saved.profilePhotoUrl?.replace(/^\/api\/v2\/people\/(?!browse\/|self\/)/, '/api/v2/people/browse/') })
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

export default function PersonProfile({ entry, onPersonChange, publicQuotesOnly, onTogglePublicQuotes }) {
  const [person, setPerson] = useState(entry.person)
  const [profile, setProfile] = useState(null)
  const [section, setSection] = useState('profile')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const [profileError, setProfileError] = useState('')
  const [quoteRefresh, setQuoteRefresh] = useState(0)
  const [votingId, setVotingId] = useState(null)
  const [voteError, setVoteError] = useState('')
  const [superpowers, setSuperpowers] = useState([])
  const [dictionary, setDictionary] = useState([])
  const [selectedSuperpower, setSelectedSuperpower] = useState('')
  const [superpowerBusy, setSuperpowerBusy] = useState(false)
  const [superpowerError, setSuperpowerError] = useState('')
  const canEditSuperpowers = profile?.canEditSuperpowers === true
  const requestKey = `${person.id}:${section}:${page}:${retry}:${section === 'quotes' && publicQuotesOnly}`
  const refresh = section === 'quotes' ? quoteRefresh : 0
  const data = result?.key === requestKey ? result.data : null

  async function voteForQuote(id, vote, retract) {
    setVotingId(id)
    setVoteError('')
    try {
      await api(`/quotes/${id}/vote`, retract ? { method: 'DELETE' } : { method: 'PUT', body: { vote } })
      setQuoteRefresh(value => value + 1)
    } catch (failure) {
      setVoteError(failure.message)
      if (failure.status === 409) setQuoteRefresh(value => value + 1)
    } finally {
      setVotingId(null)
    }
  }
  useEffect(() => {
    const controller = new AbortController()
    setProfileError('')
    api(`/people/browse/${person.id}`, { signal: controller.signal }).then(value => { if (!controller.signal.aborted) { setProfile(value); setPerson(value.person) } })
      .catch(failure => { if (!controller.signal.aborted) setProfileError(failure.message) })
    return () => controller.abort()
  }, [person.id, retry])
  useEffect(() => {
    const controller = new AbortController()
    setSuperpowerError('')
    const requests = [api(`/people/${person.id}/superpowers`, { signal: controller.signal })]
    if (canEditSuperpowers) requests.push(api('/superpowers', { signal: controller.signal }))
    Promise.all(requests).then(([assigned, all = []]) => {
      if (controller.signal.aborted) return
      setSuperpowers(assigned)
      setDictionary(all)
      setSelectedSuperpower('')
    }).catch(failure => { if (!controller.signal.aborted) setSuperpowerError(failure.message) })
    return () => controller.abort()
  }, [person.id, canEditSuperpowers])
  const assignedSuperpowerIds = new Set(superpowers.map(item => String(item.superpowerId)))
  const availableSuperpowers = dictionary.filter(item => !assignedSuperpowerIds.has(String(item.id)))

  async function addSuperpower(event) {
    event.preventDefault()
    if (!selectedSuperpower) return
    setSuperpowerBusy(true)
    setSuperpowerError('')
    try {
      const saved = await api(`/people/${person.id}/superpowers`, { body: { superpowerId: selectedSuperpower } })
      setSuperpowers(current => [...current, saved])
      setSelectedSuperpower('')
    } catch (failure) {
      setSuperpowerError(failure.message)
    } finally {
      setSuperpowerBusy(false)
    }
  }

  async function removeSuperpower(superpowerId) {
    setSuperpowerBusy(true)
    setSuperpowerError('')
    try {
      await api(`/people/${person.id}/superpowers/${superpowerId}`, { method: 'DELETE' })
      setSuperpowers(current => current.filter(item => String(item.superpowerId) !== String(superpowerId)))
    } catch (failure) {
      setSuperpowerError(failure.message)
    } finally {
      setSuperpowerBusy(false)
    }
  }
  useEffect(() => {
    setError('')
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
  }, [person.id, section, page, retry, requestKey, refresh])
  return <>
    <div className="person-detail-heading"><PersonAvatar person={person} /><h2 id="person-detail-title">{person.fullName}</h2></div>
    <nav className="person-profile-tabs" aria-label="Szczegóły osoby">{sections.map(([key, label]) => <button key={key} type="button" aria-current={section === key ? 'page' : undefined} onClick={() => { setSection(key); setPage(0) }}>{label}</button>)}</nav>
    {profileError && <p role="alert" className="field-error">{profileError} <button onClick={() => setRetry(value => value + 1)}>Spróbuj ponownie</button></p>}
    {section === 'profile' ? <>
      {profile && <p>{profile.lastSeen
        ? <>Ostatni raz widzieliście się {profile.lastSeen.daysAgo} {profile.lastSeen.daysAgo === 1 ? 'dzień' : 'dni'} temu. Okazją do tego było {profile.lastSeen.interactionName}.</>
        : 'Brak zapisanych wspólnych interakcji.'}</p>}
      <dl className="person-detail-fields">
        {entry.visitCount != null && <div><dt>Twoje wspólne wizyty</dt><dd>{entry.visitCount}</dd></div>}
        {person.birthdayDate && <div><dt>Data urodzenia</dt><dd>{date(person.birthdayDate)}</dd></div>}
        {person.livesIn && <div><dt>Mieszka w</dt><dd>{person.livesIn}</dd></div>}
        {person.home && <div><dt>Dom</dt><dd>{person.home.name}</dd></div>}
        {person.clique && <div><dt>Klika</dt><dd>
          {profile?.cliqueIcon?.photoUrl && <img key={profile.cliqueIcon.photoUrl} className="person-clique-icon"
            src={profile.cliqueIcon.photoUrl} alt="" width={32} height={32} onError={event => { event.currentTarget.hidden = true }} />}
          {person.clique.cliqueName}
        </dd></div>}
      </dl>
      {superpowers.length > 0 && <ul className="superpower-icons" aria-label={t('people.superpowers')}>
        {superpowers.map(item => <li key={item.superpowerId} title={item.superpowerName} aria-label={item.superpowerName}>
          {item.photoUrl
            ? <img src={item.photoUrl} alt="" width={36} height={36} loading="lazy" />
            : <span aria-hidden="true">{item.superpowerName.charAt(0)}</span>}
        </li>)}
      </ul>}
      {/^(https?:\/\/)/i.test(person.fbLink || '') && <a href={person.fbLink} target="_blank" rel="noopener noreferrer">Profil na Facebooku ↗</a>}
      <section className="superpower-editor" aria-labelledby="person-superpowers-title">
        <h3 id="person-superpowers-title">Supermoce</h3>
        {superpowerError && <p className="field-error" role="alert">{superpowerError}</p>}
        {superpowers.length ? <ul className="superpower-list">
          {superpowers.map(item => <li key={item.superpowerId}>
            {item.photoUrl && <img src={item.photoUrl} alt="" width="40" height="40" loading="lazy" style={{ borderRadius: 6, objectFit: 'cover' }} />}<span>{item.superpowerName}</span>
            {canEditSuperpowers && <button type="button" className="text-button superpower-remove" disabled={superpowerBusy} onClick={() => removeSuperpower(item.superpowerId)}>Usuń</button>}
          </li>)}
        </ul> : <p className="field-hint">Brak przypisanych supermocy.</p>}
        {canEditSuperpowers && <form className="superpower-add" onSubmit={addSuperpower}>
          <label htmlFor="superpower-picker">Dodaj supermoc</label>
          <select id="superpower-picker" value={selectedSuperpower} disabled={superpowerBusy} onChange={event => { setSelectedSuperpower(event.target.value); setSuperpowerError('') }}>
            <option value="">Wybierz supermoc…</option>
            {availableSuperpowers.map(item => <option key={item.id} value={item.id}>{item.superpowerName}</option>)}
          </select>
          <button className="primary" type="submit" disabled={superpowerBusy || !selectedSuperpower}>{superpowerBusy ? 'Zapisywanie…' : 'Dodaj'}</button>
        </form>}
      </section>
      {profile?.canEditPhoto && <PhotoForm person={person} onSave={saved => { setPerson(saved); onPersonChange(saved) }} />}
      {profile && !profile.canEditPhoto && <p className="field-hint">Zdjęcie może zmienić właściciel rekordu osoby lub administrator.</p>}
      {!profile && !profileError && <p role="status" className="field-hint">Sprawdzanie uprawnień do zmiany zdjęcia…</p>}
    </> : <>
      {section === 'quotes' && <button type="button" className="public-quotes-toggle" aria-pressed={publicQuotesOnly}
        onClick={() => { setPage(0); setVoteError(''); onTogglePublicQuotes() }}>Tylko publiczne cytaty</button>}
      {loading && !data && <p role="status">Wczytywanie…</p>}
      {section === 'quotes' && voteError && <p className="field-error" role="alert">{voteError}</p>}
      {error && <p className="field-error" role="alert">{error} <button onClick={() => setRetry(value => value + 1)}>Spróbuj ponownie</button></p>}
      {data && (section === 'map' ? <><p className="field-hint">Mapa wszystkich dostępnych wizyt, spotkań, eventów i podróży. Pokazujemy wyłącznie zapisane miejsca ze współrzędnymi.</p><TripMap places={data} title="Mapa interakcji" emptyMessage="Brak dostępnych interakcji z miejscami na mapie." /></> : <>
        <p role="status">Liczba wyników: {data.totalElements}</p>
        {!data.content.length && <p>Brak dostępnych {section === 'quotes' ? 'cytatów' : 'interakcji'}.</p>}
        {data.content.map(item => section === 'quotes' ? <article className="card profile-entry person-public-quote" key={item.id}>
          <small>{item.isPublic ? 'PUBLICZNY CYTAT' : 'PRYWATNY CYTAT'}</small>
          <QuoteContent quote={item.quote} tabIndex={0} />
          <div className="quote-vote" role="group" aria-label="Ocena cytatu" aria-busy={votingId === item.id}>
            <button type="button" aria-label={item.myVote === 1 ? 'Cofnij plusa' : 'Daj plusa'} aria-pressed={item.myVote === 1}
              disabled={item.myVote === -1 || votingId != null || loading} onClick={() => voteForQuote(item.id, 'PLUS', item.myVote === 1)}>+</button>
            <span aria-label={`Wynik: ${item.score}`}>{item.score > 0 ? `+${item.score}` : item.score}</span>
            <button type="button" aria-label={item.myVote === -1 ? 'Cofnij minusa' : 'Daj minusa'} aria-pressed={item.myVote === -1}
              disabled={item.myVote === 1 || votingId != null || loading} onClick={() => voteForQuote(item.id, 'MINUS', item.myVote === -1)}>−</button>
          </div>
          {item.myVote != null && <small>Twój głos: {item.myVote === 1 ? 'plus' : 'minus'}</small>}
          {votingId === item.id && <small role="status">Zapisywanie głosu…</small>}
        </article> : <article className="card profile-entry" key={item.id}>
          <h3>{item.name}</h3><p>{date(item.startDate)}{item.endDate && item.endDate.slice(0, 10) !== item.startDate.slice(0, 10) ? ` — ${date(item.endDate)}` : ''}</p>
          {(item.place || item.details.placeString) && <p>{[item.place?.name, item.details.placeString].filter(Boolean).join(' · ')}</p>}
          {item.longDesc && <p className="memory-description">{item.longDesc}</p>}
          {item.canAddParticipants ? <ParticipantsEditor entry={interactionEntry(item)} allowRemoval={false} onChange={updated => {
            setResult(current => current?.key === requestKey ? { ...current, data: { ...current.data, content: current.data.content.map(existing => existing.id === item.id ? { ...existing, participants: updated.participants } : existing) } } : current)
          }} /> : <div className="calendar-detail-people">{item.participants.map(member => <span className="calendar-person" key={member.id}><PersonAvatar person={member} /><span>{member.fullName}</span></span>)}</div>}
          {item.type === 'TRIP' && <TripMap places={item.details.places} />}
          {item.details.photoUrl && <img className="memory-photo" src={item.details.photoUrl} alt={item.details.photoName || item.name} loading="lazy" />}
        </article>)}
        {(page > 0 || data.hasNext) && <div className="event-photo-pages"><button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Poprzednia strona</button><span>Strona {page + 1}</span><button disabled={!data.hasNext} onClick={() => setPage(value => value + 1)}>Następna strona</button></div>}
      </>)}
    </>}
  </>
}
