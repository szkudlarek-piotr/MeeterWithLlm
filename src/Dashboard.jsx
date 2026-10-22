import { useEffect, useRef, useState } from 'react'
import { api } from './api.js'
import HumanForm from './HumanForm.jsx'
import CalendarPanel, { PersonAvatar } from './CalendarPanel.jsx'
import { CliqueForm, PlaceForm } from './EntityForms.jsx'
import InteractionForm, { interactionEntry, interactionTypes } from './InteractionForm.jsx'
import QuoteForm from './QuoteForm.jsx'
import QuoteGamePanel from './QuoteGamePanel.jsx'
import PeoplePanel from './PeoplePanel.jsx'
import NewsPanel from './NewsPanel.jsx'

const categories = [
  ['Ludzie', 'Dodaj osobę', '◎', 'Imię i nazwisko'],
  ['Wizyty', 'Dodaj wizytę', '⌂', 'Nazwa wizyty'],
  ['Spotkania', 'Dodaj spotkanie', '☕', 'Nazwa spotkania'],
  ['Eventy', 'Dodaj event', '✦', 'Nazwa eventu'],
  ['Miejsca', 'Dodaj miejsce', '⌖', 'Nazwa miejsca'],
  ['Supermoce', 'Dodaj supermoc', 'ϟ', 'Nazwa supermocy'],
  ['Podróże', 'Dodaj podróż', '↗', 'Nazwa podróży'],
  ['Kliki', 'Dodaj klikę', '◌', 'Nazwa kliki'],
  ['Współlokatorzy', 'Dodaj wspólne mieszkanie', '⌂', 'Wspólne mieszkanie'],
  ['Śluby', 'Dodaj ślub', '♡', 'Nazwa ślubu'],
  ['Cytaty', 'Dodaj cytat', '❝', 'Treść cytatu'],
]
export default function Dashboard({ email, actorId }) {
  const [people, setPeople] = useState([])
  const [, setHumans] = useState([])
  const [cliques, setCliques] = useState([])
  const [places, setPlaces] = useState([])
  const [formVersion, setFormVersion] = useState(0)
  const [apiError, setApiError] = useState('')
  const [query, setQuery] = useState('')
  const [calendarEntries, setCalendarEntries] = useState([])
  const [calendarLoading, setCalendarLoading] = useState(false)
  const [calendarError, setCalendarError] = useState('')
  const [calendarVersion, setCalendarVersion] = useState(0)
  const [notice, setNotice] = useState('')
  const [view, setView] = useState('feed')
  const [category, setCategory] = useState(categories[0])
  const [month, setMonth] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1))
  const [selectedDate, setSelectedDate] = useState('')
  const formDialog = useRef(null)
  const [matches, setMatches] = useState([])
  const [searching, setSearching] = useState(false)
  const [searchError, setSearchError] = useState('')
  const directoryVisible = person => person.clique?.cliqueName?.trim().toLowerCase() !== 'my_exes'
  const visiblePeople = view === 'people' ? people.filter(directoryVisible) : people
  const visibleMatches = view === 'people' ? matches.filter(directoryVisible) : matches
  const dateKey = day => `${month.getFullYear()}-${String(month.getMonth() + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  const offset = (new Date(month.getFullYear(), month.getMonth(), 1).getDay() + 6) % 7
  const days = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate()

  useEffect(() => {
    const controller = new AbortController()
    Promise.all(['/people?limit=100', '/cliques?limit=100', '/places?limit=100'].map(path => api(path, { signal: controller.signal })))
      .then(([humans, groups, locations]) => {
        setPeople(humans); setCliques(groups); setPlaces(locations)
      }).catch(error => { if (error.name !== 'AbortError') setApiError(error.message) })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    setMatches([])
    setSearchError('')
    if (!query.trim()) { setSearching(false); return () => controller.abort() }
    setSearching(true)
    api(`/people?q=${encodeURIComponent(query)}&limit=100`, { signal: controller.signal })
      .then(setMatches)
      .catch(error => { if (!controller.signal.aborted) setSearchError(error.message) })
      .finally(() => { if (!controller.signal.aborted) setSearching(false) })
    return () => controller.abort()
  }, [query, people])


  useEffect(() => {
    if (view !== 'calendar') return
    const controller = new AbortController()
    setCalendarLoading(true); setCalendarError(''); setCalendarEntries([])
    async function loadMonth() {
      const incoming = []
      let page = 0
      let more = true
      while (more) {
        const result = await api(`/interactions?from=${dateKey(1)}&to=${dateKey(days)}&size=100&page=${page}`, { signal: controller.signal })
        incoming.push(...result.content.filter(item => item.type !== 'FLATMATE').map(interactionEntry)); more = result.hasNext; page++
      }
      if (!controller.signal.aborted) setCalendarEntries(incoming)
    }
    loadMonth().catch(error => { if (!controller.signal.aborted) setCalendarError(error.message) })
      .finally(() => { if (!controller.signal.aborted) setCalendarLoading(false) })
    return () => controller.abort()
  }, [view, month, calendarVersion])

  function openForm(item) {
    setCategory(item)
    setFormVersion(current => current + 1)
    formDialog.current.showModal()
  }

  function save(event) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const name = String(data.get('name')).trim()
    if (!name) return
    event.currentTarget.reset()
    formDialog.current.close()
  }

  async function saveHuman(human) {
    human = await api('/people', { body: {
      name: human.name, surname: human.surname, birthdayDate: human.birthdayDate,
      fbLink: human.fbLink, gender: human.gender, livesIn: human.livesIn,
      homeId: human.home?.id ?? null, cliqueId: human.clique?.id ?? null,
      profilePhotoUrl: human.profilePhotoUrl,
    } })
    setApiError('')
    const fullName = human.fullName
    setHumans(current => [...current, human])
    setPeople(current => [...current, human])
    formDialog.current.close()
  }

  async function saveClique(clique) {
    clique = await api('/cliques', { body: { cliqueName: clique.cliqueName, cliqueCapitalId: clique.cliqueCapital?.id ?? null } })
    setApiError('')
    setCliques(current => [...current, clique])
    formDialog.current.close()
  }

  async function savePlace(place) {
    place = await api('/places', { body: place })
    setApiError('')
    setPlaces(current => [...current, place])
    formDialog.current.close()
  }

  async function saveInteraction(endpoint, body) {
    const saved = await api(endpoint, { body })
    setCalendarVersion(current => current + 1)
    setNotice(`Zapisano: ${saved.name}`)
    formDialog.current.close()
  }

  function updateInteraction(entry) {
    setCalendarEntries(current => current.map(item => item.id === entry.id ? entry : item))
  }

  async function saveQuote(body) {
    await api('/quotes', { body })
    setCalendarVersion(current => current + 1)
    setView('people')
    setNotice('Zapisano cytat.')
    formDialog.current.close()
  }

  return (
    <>
      <div className={`dashboard ${view === 'calendar' ? 'calendar-layout' : ''}`}>
        <aside className="sidebar">
          <div className="profile"><span className="avatar">{email[0].toUpperCase()}</span><div><strong>Twój Meeter</strong><small>{email}</small></div></div>
          <nav aria-label="Menu główne">
            <button className={`nav-item ${view === 'people' ? 'active' : ''}`} onClick={() => setView('people')}><span>◎</span> Ludzie</button>
            <button className={`nav-item ${view === 'feed' ? 'active' : ''}`} onClick={() => setView('feed')}><span>▦</span> Aktualności</button>
            <button className={`nav-item ${view === 'calendar' ? 'active' : ''}`} onClick={() => setView('calendar')}><span>▤</span> Kalendarz</button>
            <button className={`nav-item ${view === 'game' ? 'active' : ''}`} onClick={() => setView('game')}><span>🎲</span> Zgadnij autora</button>
          </nav>
          <p className="sidebar-heading">DODAJ DO SWOJEGO ŚWIATA</p>
          <nav aria-label="Dodawanie">
            {categories.map(item => <button key={item[0]} className="nav-item" onClick={() => openForm(item)}><span>{item[2]}</span>{item[1]}<span className="nav-plus">+</span></button>)}
          </nav>
          <div className="sidebar-note"><span>✧</span><p>Każde spotkanie<br />to nowa historia.</p></div>
        </aside>

        <section className="dashboard-main">
          {notice && <p className="save-notice" role="status">✓ {notice}</p>}
          {apiError && <p className="field-error" role="alert">{apiError}</p>}
          <div className="search-area">
            <label className="friend-search"><span aria-hidden="true">⌕</span><input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Szukaj znajomych…" aria-label="Szukaj znajomych" /></label>
            {query.trim() && <div className="search-results" aria-live="polite"><small>Znajomi · {visibleMatches.length}</small>{visibleMatches.length ? visibleMatches.map(person => <div className="person" key={person.id}><PersonAvatar person={person} /><span>{person.fullName}{person.demo && <small className="sample-person">przykład</small>}</span></div>) : <p>{searching ? 'Wyszukiwanie…' : searchError || 'Nie znaleziono znajomych.'}</p>}</div>}
          </div>
          <div className="page-heading"><div><p className="eyebrow">TWÓJ MAŁY WIELKI ŚWIAT</p><h2>{view === 'people' ? 'Twoi ludzie.' : view === 'feed' ? 'Co nowego w Meeterze?' : view === 'game' ? 'Kto to powiedział?' : 'Czas na wspólne chwile.'}</h2><p>{view === 'people' ? 'Przeglądaj znajomych i wspólne wizyty.' : view === 'feed' ? 'Nowe funkcje, poprawki i usprawnienia w jednym miejscu.' : view === 'game' ? 'Zgadnij autora. Sprawdź, jak dobrze znasz swoich ludzi.' : 'Twoje spotkania, wizyty, eventy i podróże w jednym miejscu.'}</p></div><span className="demo-badge">Twój Meeter</span></div>

          {view === 'people' ? <PeoplePanel version={people.length + calendarVersion} onAdd={() => openForm(categories[0])} /> : view === 'game' ? <QuoteGamePanel /> : view === 'feed' ? <NewsPanel /> : <>
            <CalendarPanel month={month} entries={calendarEntries} selectedDate={selectedDate} actorId={actorId} onSelect={setSelectedDate} onMonthChange={next => { setMonth(next); setSelectedDate('') }} loading={calendarLoading} error={calendarError} retry={() => setCalendarVersion(current => current + 1)} onAdd={() => openForm(categories[2])} onInteractionChange={updateInteraction} />
          </>}
        </section>

        <aside className="rightbar"><section className="card people-card"><div className="section-title"><h3>Twoi ludzie</h3><span>{visiblePeople.length}</span></div>{visiblePeople.map(person => <div key={person.id} className="person"><PersonAvatar person={person} /><span>{person.fullName}{person.demo && <small className="sample-person">przykład</small>}</span></div>)}<button className="text-button" onClick={() => openForm(categories[0])}>+ Dodaj nową osobę</button></section><section className="connection-note"><span>↗</span><h3>Bliskość zaczyna się<br />od małych gestów.</h3><p>Dobry moment, żeby zaplanować kolejne spotkanie.</p><button className="text-button" onClick={() => openForm(categories[2])}>Zaplanuj coś miłego →</button></section><p className="data-note">Interakcje zapisują się w Twojej bazie i pozostają po odświeżeniu strony. Kalendarz pokazuje też wpisy wielodniowe.</p></aside>
      </div>

      <dialog ref={formDialog} className="login-dialog" aria-labelledby="add-title"><button className="close-dialog" aria-label="Zamknij" onClick={() => formDialog.current.close()}>×</button><p className="eyebrow">NOWA HISTORIA</p><h2 id="add-title">{category[1]}</h2>{category[0] === 'Ludzie' ? <HumanForm key={formVersion} onSave={saveHuman} cliques={cliques} places={places} /> : category[0] === 'Kliki' ? <CliqueForm key={formVersion} places={places} onSave={saveClique} /> : category[0] === 'Miejsca' ? <PlaceForm key={formVersion} onSave={savePlace} /> : category[0] === 'Cytaty' ? <QuoteForm key={formVersion} onSave={saveQuote} /> : interactionTypes[category[0]] ? <InteractionForm key={formVersion} category={category[0]} actorId={actorId} initialDate={selectedDate} onSave={saveInteraction} /> : <form onSubmit={save}><label htmlFor="entry-name">{category[3]}</label><input id="entry-name" name="name" required maxLength={120} /><label htmlFor="entry-date">Data (opcjonalnie)</label><input key={selectedDate} id="entry-date" type="date" name="date" defaultValue={selectedDate} /><label htmlFor="entry-note">Notatka (opcjonalnie)</label><textarea id="entry-note" name="note" rows={3} maxLength={1000} /><p className="demo-note">Wpis zostanie dodany do wersji demo na czas tej sesji.</p><button className="primary submit">Zapisz</button></form>}</dialog>
    </>
  )
}








