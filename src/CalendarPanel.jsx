import { useEffect, useRef, useState } from 'react'
import { api } from './api.js'
import EntityPicker from './EntityPicker.jsx'
import TripMap from './TripMap.jsx'
import { interactionEntry } from './InteractionForm.jsx'

const types = {
  MEETING: ['Spotkanie', '☕'], VISIT: ['Wizyta', '⌂'], EVENT: ['Event', '✦'],
  TRIP: ['Podróż', '↗'], WEDDING: ['Ślub', '♡'],
}
const keyOf = date => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const occurs = (entry, date) => entry.date <= date && (entry.ongoing || date <= (entry.endDate || entry.date))
const fullDate = date => new Date(`${date}T12:00:00`).toLocaleDateString('pl-PL', { day: 'numeric', month: 'long', year: 'numeric' })

export function PersonAvatar({ person }) {
  const [failed, setFailed] = useState(false)
  const photo = person.profilePhotoUrl || person.photoUrl
  useEffect(() => setFailed(false), [photo])
  const initials = person.fullName.split(' ').filter(Boolean).map(part => part[0]).slice(0, 2).join('')
  return <span className="calendar-avatar" title={person.fullName}>
    {photo && !failed ? <img src={photo} alt={person.fullName} loading="lazy" onError={() => setFailed(true)} /> : <span aria-label={person.fullName}>{initials}</span>}
  </span>
}

function peopleOf(entries, actorId) {
  const people = entries.flatMap(entry => entry.participants || [])
  const unique = [...new Map(people.map(person => [person.id, person])).values()]
  const others = unique.filter(person => person.id !== actorId)
  return others.length ? others : unique
}

function ParticipantsEditor({ entry, actorId, onChange }) {
  const [people, setPeople] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  async function change(person) {
    if (busy) return
    setBusy(true); setError(''); setNotice('')
    try {
      if (person) {
        await api(`/interactions/${entry.interactionId}/participants/${person.id}`, { method: 'DELETE' })
        onChange({ ...entry, participants: entry.participants.filter(item => item.id !== person.id), removableParticipantIds: entry.removableParticipantIds.filter(id => id !== person.id) })
        setNotice(`Usunięto uczestnika: ${person.fullName}.`)
      } else {
        const saved = await api(`/interactions/${entry.interactionId}/participants`, { body: { participantIds: people.map(item => item.id) } })
        onChange(interactionEntry(saved))
        setPeople([]); setNotice('Dodano uczestników.')
      }
    } catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }
  return <div className="participant-editor" aria-busy={busy}>
    <fieldset disabled={busy}>
      <legend>Uczestnicy</legend>
      <div className="calendar-detail-people">{(entry.participants || []).map(person => <span className="calendar-person" key={person.id}>
        <PersonAvatar person={person} /><span>{person.fullName}</span>
        {entry.removableParticipantIds.includes(person.id) && <button type="button" className="participant-remove" aria-label={`Usuń uczestnika: ${person.fullName}`} onClick={() => change(person)}>{person.id === actorId ? 'Usuń mnie' : 'Usuń'}</button>}
      </span>)}</div>
      {!entry.participants?.length && <p className="calendar-empty-hint">Brak uczestników.</p>}
      {entry.canAddParticipants && <form onSubmit={event => { event.preventDefault(); if (people.length) change() }}>
        <EntityPicker label="Dodaj uczestników" source="people" value={people} onChange={setPeople} multiple excludeIds={(entry.participants || []).map(person => person.id)} />
        <button type="submit" className="primary" disabled={busy || !people.length}>{busy ? 'Zapisywanie…' : 'Dodaj wybrane osoby'}</button>
      </form>}
    </fieldset>
    {error && <p className="field-error" role="alert">{error}</p>}
    {notice && <p className="participant-notice" role="status">{notice}</p>}
  </div>
}

export default function CalendarPanel({ month, entries, selectedDate, onSelect, onMonthChange, loading, error, retry, actorId, onAdd, onInteractionChange }) {
  const dayDialog = useRef(null)
  useEffect(() => {
    if (!selectedDate) dayDialog.current?.close()
  }, [selectedDate])
  function selectDay(date) {
    onSelect(date)
    if (!dayDialog.current.open) dayDialog.current.showModal()
  }
  function addMeeting() {
    dayDialog.current.close()
    onAdd()
  }
  const startOffset = (new Date(month.getFullYear(), month.getMonth(), 1).getDay() + 6) % 7
  const numberOfDays = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate()
  const selected = selectedDate ? entries.filter(entry => occurs(entry, selectedDate)) : []
  const today = keyOf(new Date())
  const monthCount = entries.length
  const perDay = Array.from({ length: numberOfDays }, (_, index) => {
    const date = keyOf(new Date(month.getFullYear(), month.getMonth(), index + 1))
    return { date, items: entries.filter(entry => occurs(entry, date)) }
  })

  return <section className="memory-calendar">
    <div className="card calendar-month-card">
      <div className="memory-calendar-toolbar">
        <div><p className="eyebrow">LUDZIE, KTÓRZY WYPEŁNIAJĄ DNI</p><h3>{month.toLocaleDateString('pl-PL', { month: 'long', year: 'numeric' })}</h3><p className="calendar-subtitle">{loading ? 'Otwieram wspomnienia…' : `${monthCount} zapisanych interakcji w tym miesiącu`}</p></div>
        <div className="calendar-controls"><button aria-label="Poprzedni miesiąc" onClick={() => onMonthChange(new Date(month.getFullYear(), month.getMonth() - 1, 1))}>←</button><button onClick={() => { const now = new Date(); onMonthChange(new Date(now.getFullYear(), now.getMonth(), 1)); selectDay(today) }}>Dziś</button><button aria-label="Następny miesiąc" onClick={() => onMonthChange(new Date(month.getFullYear(), month.getMonth() + 1, 1))}>→</button></div>
      </div>
      {error && <p className="field-error" role="alert">{error} <button className="text-button" onClick={retry}>Spróbuj ponownie</button></p>}
      <div className="memory-calendar-grid" aria-label="Dni miesiąca" aria-busy={loading}>
        {['Pon', 'Wt', 'Śr', 'Czw', 'Pt', 'Sob', 'Niedz'].map(day => <span className="weekday" key={day}>{day}</span>)}
        {Array.from({ length: startOffset }, (_, index) => <span className="calendar-spacer" key={`blank-${index}`} />)}
        {perDay.map(({ date, items }, index) => {
          const people = peopleOf(items, actorId)
          const kinds = [...new Set(items.map(item => item.type))]
          return <button key={date} className={`memory-day ${items.length ? 'filled' : ''} ${selectedDate === date ? 'selected' : ''} ${date === today ? 'today' : ''}`} aria-label={`${fullDate(date)}, ${items.length} interakcji`} aria-haspopup="dialog" aria-pressed={selectedDate === date} onClick={() => selectDay(date)}>
            <span className="memory-day-number">{index + 1}{items.length > 0 && <small>{items.length}</small>}</span>
            {people.length > 0 && <span className="calendar-people">{people.slice(0, 3).map(person => <PersonAvatar key={person.id} person={person} />)}{people.length > 3 && <span className="more-people">+{people.length - 3}</span>}</span>}
            {items.length > 0 && <span className="day-preview">{items[0].name}</span>}
            <span className="day-types">{kinds.map(type => <span key={type} className={`type-mark type-${type.toLowerCase()}`} title={types[type][0]} aria-label={types[type][0]}>{types[type][1]}</span>)}</span>
          </button>
        })}
      </div>
      <div className="calendar-legend">{Object.entries(types).map(([type, [label, icon]]) => <span key={type}><i className={`type-mark type-${type.toLowerCase()}`}>{icon}</i>{label}</span>)}</div>
    </div>
    <dialog ref={dayDialog} className="calendar-day-dialog" aria-labelledby="calendar-day-title" onClick={event => {
      if (event.target !== event.currentTarget) return
      const bounds = event.currentTarget.getBoundingClientRect()
      if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) dayDialog.current.close()
    }}>
      <button type="button" className="close-dialog" aria-label="Zamknij szczegóły dnia" onClick={() => dayDialog.current.close()} autoFocus>×</button>
      <div className="day-panel-heading"><div><p className="eyebrow">JEDEN DZIEŃ, WIELE HISTORII</p><h2 id="calendar-day-title">{selectedDate ? fullDate(selectedDate) : 'Wybierz dzień'}</h2></div></div>
      {selectedDate && <button className="primary" onClick={addMeeting}>+ Dodaj spotkanie</button>}
      {loading && <p role="status" className="calendar-empty-hint">Wczytywanie interakcji…</p>}
      {error && <p className="field-error" role="alert">{error} <button className="text-button" onClick={retry}>Spróbuj ponownie</button></p>}
      {!selectedDate && <p className="calendar-empty-hint">Wybierz kafelek, żeby zobaczyć spotkania, ludzi i wspomnienia.</p>}
      {selectedDate && !loading && !error && selected.length === 0 && <p className="calendar-empty-hint">Jeszcze czysta karta. Może to dobry dzień na spotkanie?</p>}
      {selected.map(entry => <article className={`card memory-detail detail-${entry.type.toLowerCase()}`} key={entry.id}>
        <div className="memory-detail-heading"><span className={`type-mark type-${entry.type.toLowerCase()}`}>{types[entry.type][1]}</span><span>{types[entry.type][0]}</span>{entry.startTime && <small>{entry.startTime}{entry.endTime && entry.endTime !== entry.startTime ? ` – ${entry.endTime}` : ''}</small>}</div>
        <h3>{entry.name}</h3>
        {entry.endDate && entry.endDate !== entry.date && <p className="memory-location">{fullDate(entry.date)} — {fullDate(entry.endDate)}</p>}
        {entry.ongoing && <p className="memory-location">Od {fullDate(entry.date)} · trwa</p>}
        {entry.type === 'WEDDING' && <p className="wedding-couple">♡ {entry.details.groom.fullName} i {entry.details.bride.fullName}</p>}
        {entry.locations?.length > 0 && <p className="memory-location">⌖ {entry.locations.join(' · ')}</p>}
        <ParticipantsEditor entry={entry} actorId={actorId} onChange={onInteractionChange} />
        {entry.note && <p className="memory-description">{entry.note}</p>}
        {entry.type === 'TRIP' && <TripMap places={entry.details.places} />}
        {entry.details.photoUrl && <img className="memory-photo" src={entry.details.photoUrl} alt={entry.details.photoName || `Wspomnienie: ${entry.name}`} loading="lazy" />}
        {entry.type === 'EVENT' && (entry.details.meComingDate || entry.details.meLeavingDate) && <p className="memory-location">Mój udział: {entry.details.meComingDate?.slice(11, 16) || 'od początku'} — {entry.details.meLeavingDate?.slice(11, 16) || 'do końca'}</p>}
        {entry.details.hadAfter && <p className="memory-location">Z poprawinami ♡</p>}
      </article>)}
    </dialog>
  </section>
}
