import { useEffect, useState } from 'react'
import { api } from './api.js'
import { PersonAvatar, ParticipantsEditor } from './CalendarPanel.jsx'
import { interactionEntry } from './InteractionForm.jsx'
import QuoteContent from './QuoteContent.jsx'
import TripMap from './TripMap.jsx'
import FamilyPanel from './FamilyPanel.jsx'
import { t, tf, dateLocale } from './i18n.js'

const sections = [['profile', t('Profil')], ['map', t('Mapa interakcji')], ['VISIT', t('Wizyty')], ['MEETING', t('Spotkania')], ['EVENT', t('Eventy')], ['TRIP', t('Podróże')], ['quotes', t('Cytaty')], ['family', t('Rodzina')]]
const date = value => new Date(`${value.slice(0, 10)}T12:00:00`).toLocaleDateString(dateLocale())

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
        if (!file) throw new Error(t('Wybierz zdjęcie.'))
        if (file.size > 4 * 1024 * 1024) throw new Error(t('Zdjęcie może mieć maksymalnie 4 MB.'))
        const data = await new Promise((resolve, reject) => {
          const reader = new FileReader()
          reader.onload = () => resolve(String(reader.result).split(',')[1])
          reader.onerror = () => reject(new Error(t('Nie udało się odczytać pliku.')))
          reader.readAsDataURL(file)
        })
        body = { photoData: data }
      }
      const saved = await api(endpoint, { body })
      onSave({ ...saved, profilePhotoUrl: saved.profilePhotoUrl?.replace(/^\/api\/v2\/people\/(?!browse\/|self\/)/, '/api/v2/people/browse/') })
      setNotice(t('Zapisano zdjęcie profilowe.'))
    } catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }
  return <form className="profile-photo-form" onSubmit={submit}>
    <fieldset disabled={busy}><legend>{t('Dodaj lub zmień zdjęcie profilowe')}</legend>
      <label>{t('Źródło zdjęcia')}<select value={source} onChange={event => { setSource(event.target.value); setError(''); setNotice('') }}><option value="file">{t('Plik z komputera')}</option><option value="url">{t('Publiczny link')}</option></select></label>
      {source === 'file' ? <label>{t('Zdjęcie JPG, PNG lub GIF (do 4 MB)')}<input type="file" accept="image/jpeg,image/png,image/gif" required onChange={event => { setFile(event.target.files[0] || null); setNotice('') }} /></label> : <label>{t('Bezpośredni adres zdjęcia')}<input type="url" required maxLength={2048} value={url} onChange={event => setUrl(event.target.value)} /></label>}
      <button className="primary" disabled={busy}>{busy ? t('Zapisywanie…') : t('Zapisz zdjęcie')}</button>
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
  const [seenWith, setSeenWith] = useState(null)
  const [seenWithError, setSeenWithError] = useState('')
  const [quoteRefresh, setQuoteRefresh] = useState(0)
  const [votingId, setVotingId] = useState(null)
  const [voteError, setVoteError] = useState('')
  const [editing, setEditing] = useState(null)
  const [editBusy, setEditBusy] = useState(false)
  const [editError, setEditError] = useState('')
  const [superpowers, setSuperpowers] = useState([])
  const [dictionary, setDictionary] = useState([])
  const [selectedSuperpower, setSelectedSuperpower] = useState('')
  const [superpowerBusy, setSuperpowerBusy] = useState(false)
  const [superpowerError, setSuperpowerError] = useState('')
  const canEditSuperpowers = profile?.canEditSuperpowers === true
  const requestKey = `${person.id}:${section}:${page}:${retry}:${section === 'quotes' && publicQuotesOnly}`
  const refresh = section === 'quotes' ? quoteRefresh : 0
  const data = result?.key === requestKey ? result.data : null

  async function saveQuoteEdit(event) {
    event.preventDefault()
    const text = editing.text.trim()
    if (text.length < 4 || text.length > 10240) { setEditError(t('Cytat powinien mieć od 4 do 10240 znaków.')); return }
    setEditBusy(true); setEditError('')
    try {
      await api(`/quotes/${editing.id}`, { method: 'PATCH', body: { quote: text } })
      setEditing(null)
      setQuoteRefresh(value => value + 1)
    } catch (failure) { setEditError(failure.message) }
    finally { setEditBusy(false) }
  }

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
    setSeenWithError('')
    api(`/people/browse/${person.id}/seen-with`, { signal: controller.signal })
      .then(value => { if (!controller.signal.aborted) setSeenWith(value) })
      .catch(failure => { if (!controller.signal.aborted) setSeenWithError(failure.message) })
    return () => controller.abort()
  }, [person.id])
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
    if (section === 'profile' || section === 'family') return
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
    <nav className="person-profile-tabs" aria-label={t('Szczegóły osoby')}>{sections.map(([key, label]) => <button key={key} type="button" aria-current={section === key ? 'page' : undefined} onClick={() => { setSection(key); setPage(0) }}>{label}</button>)}</nav>
    {profileError && <p role="alert" className="field-error">{profileError} <button onClick={() => setRetry(value => value + 1)}>{t('Spróbuj ponownie')}</button></p>}
    {section === 'profile' ? <>
      {profile && <p>{profile.lastSeen
        ? tf('Ostatni raz widzieliście się {days} {unit} temu. Okazją do tego było {name}.', { days: profile.lastSeen.daysAgo, unit: t(profile.lastSeen.daysAgo === 1 ? 'dzień' : 'dni'), name: profile.lastSeen.interactionName })
        : t('Brak zapisanych wspólnych interakcji.')}</p>}
      <dl className="person-detail-fields">
        {entry.visitCount != null && <div><dt>{t('Twoje wspólne wizyty')}</dt><dd>{entry.visitCount}</dd></div>}
        {person.birthdayDate && <div><dt>{t('Data urodzenia')}</dt><dd>{date(person.birthdayDate)}</dd></div>}
        {person.livesIn && <div><dt>{t('Mieszka w')}</dt><dd>{person.livesIn}</dd></div>}
        {person.home && <div><dt>{t('Dom')}</dt><dd>{person.home.name}</dd></div>}
        {person.clique && <div><dt>{t('Klika')}</dt><dd>
          {profile?.cliqueIcon?.photoUrl && <img key={profile.cliqueIcon.photoUrl} className="person-clique-icon"
            src={profile.cliqueIcon.photoUrl} alt="" width={32} height={32} onError={event => { event.currentTarget.hidden = true }} />}
          {person.clique.cliqueName}
        </dd></div>}
      </dl>
      <section className="seen-with" aria-labelledby="person-seen-with-title">
        <h3 id="person-seen-with-title">{t('Często widywani razem')}</h3>
        {seenWithError && <p className="field-error" role="alert">{seenWithError}</p>}
        {seenWith && (seenWith.length ? <ul className="seen-with-list">
          {seenWith.map(member => <li key={member.id}><PersonAvatar person={member} /><span>{member.fullName}</span></li>)}
        </ul> : <p className="field-hint">{t('Nie widziano jeszcze nikogo razem z tą osobą.')}</p>)}
      </section>
      {superpowers.length > 0 && <ul className="superpower-icons" aria-label={t('people.superpowers')}>
        {superpowers.map(item => <li key={item.superpowerId} title={item.superpowerName} aria-label={item.superpowerName}>
          {item.photoUrl
            ? <img src={item.photoUrl} alt="" width={36} height={36} loading="lazy" />
            : <span aria-hidden="true">{item.superpowerName.charAt(0)}</span>}
        </li>)}
      </ul>}
      {/^(https?:\/\/)/i.test(person.fbLink || '') && <a href={person.fbLink} target="_blank" rel="noopener noreferrer">{t('Profil na Facebooku ↗')}</a>}
      <section className="superpower-editor" aria-labelledby="person-superpowers-title">
        <h3 id="person-superpowers-title">{t('Supermoce')}</h3>
        {superpowerError && <p className="field-error" role="alert">{superpowerError}</p>}
        {superpowers.length ? <ul className="superpower-list">
          {superpowers.map(item => <li key={item.superpowerId}>
            {item.photoUrl && <img src={item.photoUrl} alt="" width="40" height="40" loading="lazy" style={{ borderRadius: 6, objectFit: 'cover' }} />}<span>{item.superpowerName}</span>
            {canEditSuperpowers && <button type="button" className="text-button superpower-remove" disabled={superpowerBusy} onClick={() => removeSuperpower(item.superpowerId)}>{t('Usuń')}</button>}
          </li>)}
        </ul> : <p className="field-hint">{t('Brak przypisanych supermocy.')}</p>}
        {canEditSuperpowers && <form className="superpower-add" onSubmit={addSuperpower}>
          <label htmlFor="superpower-picker">{t('Dodaj supermoc')}</label>
          <select id="superpower-picker" value={selectedSuperpower} disabled={superpowerBusy} onChange={event => { setSelectedSuperpower(event.target.value); setSuperpowerError('') }}>
            <option value="">{t('Wybierz supermoc…')}</option>
            {availableSuperpowers.map(item => <option key={item.id} value={item.id}>{item.superpowerName}</option>)}
          </select>
          <button className="primary" type="submit" disabled={superpowerBusy || !selectedSuperpower}>{superpowerBusy ? t('Zapisywanie…') : t('Dodaj')}</button>
        </form>}
      </section>
      {profile?.canEditPhoto && <PhotoForm person={person} onSave={saved => { setPerson(saved); onPersonChange(saved) }} />}
      {profile && !profile.canEditPhoto && <p className="field-hint">{t('Zdjęcie może zmienić właściciel rekordu osoby lub administrator.')}</p>}
      {!profile && !profileError && <p role="status" className="field-hint">{t('Sprawdzanie uprawnień do zmiany zdjęcia…')}</p>}
    </> : section === 'family' ? <FamilyPanel person={person} /> : <>
      {section === 'quotes' && <button type="button" className="public-quotes-toggle" aria-pressed={publicQuotesOnly}
        onClick={() => { setPage(0); setVoteError(''); onTogglePublicQuotes() }}>{t('Tylko publiczne cytaty')}</button>}
      {loading && !data && <p role="status">{t('Wczytywanie…')}</p>}
      {section === 'quotes' && voteError && <p className="field-error" role="alert">{voteError}</p>}
      {error && <p className="field-error" role="alert">{error} <button onClick={() => setRetry(value => value + 1)}>{t('Spróbuj ponownie')}</button></p>}
      {data && (section === 'map' ? <><p className="field-hint">{t('Mapa wszystkich dostępnych wizyt, spotkań, eventów, podróży i wesel. Pokazujemy wyłącznie zapisane miejsca ze współrzędnymi.')}</p><TripMap places={data} showList={false} title={t('Mapa interakcji')} emptyMessage={t('Brak dostępnych interakcji z miejscami na mapie.')} /></> : <>
        <p role="status">{tf('Liczba wyników: {total}', { total: data.totalElements })}</p>
        {!data.content.length && <p>{tf('Brak dostępnych {what}.', { what: section === 'quotes' ? t('cytatów') : t('interakcji') })}</p>}
        {data.content.map(item => section === 'quotes' ? <article className="card profile-entry person-public-quote" key={item.id}>
          <small>{item.isPublic ? t('PUBLICZNY CYTAT') : t('PRYWATNY CYTAT')}</small>
          {editing?.id === item.id ? <form className="quote-edit" onSubmit={saveQuoteEdit}>
            <label htmlFor={`quote-edit-${item.id}`}>{t('Treść cytatu')}</label>
            <textarea id={`quote-edit-${item.id}`} rows={5} required minLength={4} maxLength={10240} autoFocus disabled={editBusy}
              value={editing.text} onChange={event => setEditing({ ...editing, text: event.target.value })} />
            {editError && <p className="field-error" role="alert">{editError}</p>}
            <button className="primary" disabled={editBusy}>{editBusy ? t('Zapisywanie…') : t('Zapisz zmiany')}</button>
            <button type="button" className="text-button" disabled={editBusy} onClick={() => setEditing(null)}>{t('Anuluj edycję')}</button>
          </form> : <>
            <QuoteContent quote={item.quote} tabIndex={0} />
            {item.canEdit && <button type="button" className="text-button" onClick={() => { setEditError(''); setEditing({ id: item.id, text: item.quote }) }}>{t('Edytuj cytat')}</button>}
          </>}
          <div className="quote-vote" role="group" aria-label={t('Ocena cytatu')} aria-busy={votingId === item.id}>
            <button type="button" aria-label={item.myVote === 1 ? t('Cofnij plusa') : t('Daj plusa')} aria-pressed={item.myVote === 1}
              disabled={item.myVote === -1 || votingId != null || loading} onClick={() => voteForQuote(item.id, 'PLUS', item.myVote === 1)}>+</button>
            <span aria-label={tf('Wynik: {score}', { score: item.score })}>{item.score > 0 ? `+${item.score}` : item.score}</span>
            <button type="button" aria-label={item.myVote === -1 ? t('Cofnij minusa') : t('Daj minusa')} aria-pressed={item.myVote === -1}
              disabled={item.myVote === 1 || votingId != null || loading} onClick={() => voteForQuote(item.id, 'MINUS', item.myVote === -1)}>−</button>
          </div>
          {item.myVote != null && <small>{tf('Twój głos: {vote}', { vote: item.myVote === 1 ? t('plus') : t('minus') })}</small>}
          {votingId === item.id && <small role="status">{t('Zapisywanie głosu…')}</small>}
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
        {(page > 0 || data.hasNext) && <div className="event-photo-pages"><button disabled={page === 0} onClick={() => setPage(value => value - 1)}>{t('Poprzednia strona')}</button><span>{t('Strona')} {page + 1}</span><button disabled={!data.hasNext} onClick={() => setPage(value => value + 1)}>{t('Następna strona')}</button></div>}
      </>)}
    </>}
  </>
}
