import { useEffect, useRef, useState } from 'react'
import { api } from './api.js'
import EntityPicker from './EntityPicker.jsx'
import TripMap from './TripMap.jsx'
import InteractionEditDialog from './InteractionEditDialog.jsx'
import { interactionEntry } from './InteractionForm.jsx'
import { t, tf, dateLocale } from './i18n.js'
import PersonAvatar from './PersonAvatar.jsx'
export { default as PersonAvatar } from './PersonAvatar.jsx'

const types = {
  MEETING: [t('Spotkanie'), '☕'], VISIT: [t('Wizyta'), '⌂'], EVENT: [t('Event'), '✦'],
  TRIP: [t('Podróż'), '↗'], WEDDING: [t('Ślub'), '♡'],
}
const keyOf = date => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const occurs = (entry, date) => entry.date <= date && (entry.ongoing || date <= (entry.endDate || entry.date))
const fullDate = date => new Date(`${date}T12:00:00`).toLocaleDateString(dateLocale(), { day: 'numeric', month: 'long', year: 'numeric' })

function peopleOf(entries, actorId) {
  const people = entries.flatMap(entry => entry.participants || [])
  const unique = [...new Map(people.map(person => [person.id, person])).values()]
  const others = unique.filter(person => person.id !== actorId)
  return others.length ? others : unique
}

export function ParticipantsEditor({ entry, actorId, onChange, allowRemoval = true }) {
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
        setNotice(tf('Usunięto uczestnika: {name}.', { name: person.fullName }))
      } else {
        const saved = await api(`/interactions/${entry.interactionId}/participants`, { body: { participantIds: people.map(item => item.id) } })
        onChange(interactionEntry(saved))
        setPeople([]); setNotice(t('Dodano uczestników.'))
      }
    } catch (failure) { setError(failure.message) }
    finally { setBusy(false) }
  }
  return <div className="participant-editor" aria-busy={busy}>
    <fieldset disabled={busy}>
      <legend>{t('Uczestnicy')}</legend>
      <div className="calendar-detail-people">{(entry.participants || []).map(person => <span className="calendar-person" key={person.id}>
        <PersonAvatar person={person} /><span>{person.fullName}</span>
        {allowRemoval && entry.removableParticipantIds.includes(person.id) && <button type="button" className="participant-remove" aria-label={tf('Usuń uczestnika: {name}', { name: person.fullName })} onClick={() => change(person)}>{person.id === actorId ? t('Usuń mnie') : t('Usuń')}</button>}
      </span>)}</div>
      {!entry.participants?.length && <p className="calendar-empty-hint">{t('Brak uczestników.')}</p>}
      {entry.canAddParticipants && <form onSubmit={event => { event.preventDefault(); if (people.length) change() }}>
        <EntityPicker label={t('Dodaj uczestników')} source="people" value={people} onChange={setPeople} multiple excludeIds={(entry.participants || []).map(person => person.id)} />
        <button type="submit" className="primary" disabled={busy || !people.length}>{busy ? t('Zapisywanie…') : t('Dodaj wybrane osoby')}</button>
      </form>}
    </fieldset>
    {error && <p className="field-error" role="alert">{error}</p>}
    {notice && <p className="participant-notice" role="status">{notice}</p>}
  </div>
}

export default function CalendarPanel({ month, entries, selectedDate, onSelect, onMonthChange, loading, error, retry, actorId, onAdd, onInteractionChange }) {
  const dayDialog = useRef(null)
  const [editingId, setEditingId] = useState(null)
  const [editNotice, setEditNotice] = useState('')
  useEffect(() => {
    if (!selectedDate) dayDialog.current?.close()
  }, [selectedDate])
  function selectDay(date) {
    setEditNotice('')
    onSelect(date)
    if (!dayDialog.current.open) dayDialog.current.showModal()
  }
  function addInteraction() {
    dayDialog.current.close()
    onAdd()
  }
  const startOffset = (new Date(month.getFullYear(), month.getMonth(), 1).getDay() + 6) % 7
  const numberOfDays = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate()
  const selected = selectedDate ? entries.filter(entry => occurs(entry, selectedDate)) : []
  const today = keyOf(new Date())
  const monthCount = entries.length
  const chronologicalEntries = entries.toSorted((a, b) => a.startDate.localeCompare(b.startDate))
  const perDay = Array.from({ length: numberOfDays }, (_, index) => {
    const date = keyOf(new Date(month.getFullYear(), month.getMonth(), index + 1))
    return { date, items: chronologicalEntries.filter(entry => occurs(entry, date)) }
  })

  return <section className="memory-calendar">
    <div className="card calendar-month-card">
      <div className="memory-calendar-toolbar">
        <div><p className="eyebrow">{t('LUDZIE, KTÓRZY WYPEŁNIAJĄ DNI')}</p><h3>{month.toLocaleDateString(dateLocale(), { month: 'long', year: 'numeric' })}</h3><p className="calendar-subtitle">{loading ? t('Otwieram wspomnienia…') : tf('{count} zapisanych interakcji w tym miesiącu', { count: monthCount })}</p></div>
        <div className="calendar-controls"><button aria-label={t('Poprzedni miesiąc')} onClick={() => onMonthChange(new Date(month.getFullYear(), month.getMonth() - 1, 1))}>←</button><button onClick={() => { const now = new Date(); onMonthChange(new Date(now.getFullYear(), now.getMonth(), 1)); selectDay(today) }}>{t('Dziś')}</button><button aria-label={t('Następny miesiąc')} onClick={() => onMonthChange(new Date(month.getFullYear(), month.getMonth() + 1, 1))}>→</button></div>
      </div>
      {error && <p className="field-error" role="alert">{error} <button className="text-button" onClick={retry}>{t('Spróbuj ponownie')}</button></p>}
      <div className="memory-calendar-grid" aria-label={t('Dni miesiąca')} aria-busy={loading}>
        {['Pon', 'Wt', 'Śr', 'Czw', 'Pt', 'Sob', 'Niedz'].map(day => <span className="weekday" key={day}>{t(day)}</span>)}
        {Array.from({ length: startOffset }, (_, index) => <span className="calendar-spacer" key={`blank-${index}`} />)}
        {perDay.map(({ date, items }, index) => {
          const people = peopleOf(items, actorId)
          const kinds = [...new Set(items.map(item => item.type))]
          const names = items.map(item => item.name)
          const preview = names.length > 1 ? names.slice(0, -1).join(', ') + ' ' + t('oraz') + ' ' + names.at(-1) : names[0]
          return <button key={date} className={`memory-day ${items.length ? 'filled' : ''} ${selectedDate === date ? 'selected' : ''} ${date === today ? 'today' : ''}`} aria-label={tf('{date}, {count} interakcji', { date: fullDate(date), count: items.length })} aria-haspopup="dialog" aria-pressed={selectedDate === date} onClick={() => selectDay(date)}>
            <span className="memory-day-number">{index + 1}{items.length > 0 && <small>{items.length}</small>}</span>
            {people.length > 0 && <span className="calendar-people">{people.slice(0, 3).map(person => <PersonAvatar key={person.id} person={person} />)}{people.length > 3 && <span className="more-people">+{people.length - 3}</span>}</span>}
            {items.length > 0 && <span className={names.length > 1 ? 'day-preview day-preview-list' : 'day-preview'} title={preview}>
              {preview}
            </span>}
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
      <button type="button" className="close-dialog" aria-label={t('Zamknij szczegóły dnia')} onClick={() => dayDialog.current.close()} autoFocus>×</button>
      <div className="day-panel-heading"><div><p className="eyebrow">{t('JEDEN DZIEŃ, WIELE HISTORII')}</p><h2 id="calendar-day-title">{selectedDate ? fullDate(selectedDate) : t('Wybierz dzień')}</h2></div></div>
      {selectedDate && <button className="primary" onClick={addInteraction}>+ {t('Dodaj interakcję')}</button>}
      {editNotice && <p className="participant-notice" role="status">{editNotice}</p>}
      {loading && <p role="status" className="calendar-empty-hint">{t('Wczytywanie interakcji…')}</p>}
      {error && <p className="field-error" role="alert">{error} <button className="text-button" onClick={retry}>{t('Spróbuj ponownie')}</button></p>}
      {!selectedDate && <p className="calendar-empty-hint">{t('Wybierz kafelek, żeby zobaczyć spotkania, ludzi i wspomnienia.')}</p>}
      {selectedDate && !loading && !error && selected.length === 0 && <p className="calendar-empty-hint">{t('Jeszcze czysta karta. Może to dobry dzień na spotkanie?')}</p>}
      {selected.map(entry => <article className={`card memory-detail detail-${entry.type.toLowerCase()}`} key={entry.id}>
        <div className="memory-detail-heading"><span className={`type-mark type-${entry.type.toLowerCase()}`}>{types[entry.type][1]}</span><span>{types[entry.type][0]}</span>{entry.startTime && <small>{entry.startTime}{entry.endTime && entry.endTime !== entry.startTime ? ` – ${entry.endTime}` : ''}</small>}</div>
        <h3>{entry.name}</h3>
        {entry.canAddParticipants && <button type="button" className="text-button" onClick={() => setEditingId(entry.interactionId)}>{t('Edytuj')}</button>}
        {entry.endDate && entry.endDate !== entry.date && <p className="memory-location">{fullDate(entry.date)} — {fullDate(entry.endDate)}</p>}
        {entry.ongoing && <p className="memory-location">Od {fullDate(entry.date)} · trwa</p>}
        {entry.type === 'WEDDING' && <p className="wedding-couple">♡ {entry.details.groom.fullName} {t('i')} {entry.details.bride.fullName}</p>}
        {entry.locations?.length > 0 && <p className="memory-location">⌖ {entry.locations.join(' · ')}</p>}
        <ParticipantsEditor entry={entry} actorId={actorId} onChange={onInteractionChange} />
        {entry.note && <p className="memory-description">{entry.note}</p>}
        {entry.type === 'TRIP' && <TripMap places={entry.details.places} />}
        {entry.details.photoUrl && <img className="memory-photo" src={entry.details.photoUrl} alt={entry.details.photoName || `Wspomnienie: ${entry.name}`} loading="lazy" />}
        {entry.type === 'EVENT' && (entry.details.meComingDate || entry.details.meLeavingDate) && <p className="memory-location">{t('Mój udział')}: {entry.details.meComingDate?.slice(11, 16) || t('od początku')} — {entry.details.meLeavingDate?.slice(11, 16) || t('do końca')}</p>}
        {entry.details.hadAfter && <p className="memory-location">{t('Z poprawinami')} ♡</p>}
      </article>)}
    </dialog>
    {editingId !== null && <InteractionEditDialog key={editingId} interactionId={editingId} actorId={actorId} onClose={() => setEditingId(null)} onSaved={entry => { onInteractionChange(entry); setEditNotice(`Zapisano zmiany: ${entry.name}.`) }} />}
  </section>
}
