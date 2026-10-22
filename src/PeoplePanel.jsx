import { useEffect, useRef, useState } from 'react'
import { api } from './api.js'
import { PersonAvatar } from './CalendarPanel.jsx'
import QuoteContent from './QuoteContent.jsx'
import PersonProfile from './PersonProfile.jsx'

export default function PeoplePanel({ onAdd, version }) {
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const [selected, setSelected] = useState(null)
  const personDialog = useRef(null)
  function openPerson(entry) {
    setSelected(entry)
    personDialog.current.showModal()
  }
  useEffect(() => {
    const controller = new AbortController()
    setLoading(true); setError(''); setResult(null)
    const timer = setTimeout(() => {
      api(`/people/browse?q=${encodeURIComponent(query)}&page=${page}&size=20`, { signal: controller.signal })
        .then(setResult).catch(error => { if (!controller.signal.aborted) setError(error.message) })
        .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    }, 200)
    return () => { clearTimeout(timer); controller.abort() }
  }, [query, page, retry, version])
  return <section aria-label="Przeglądaj ludzi">
    <div className="people-toolbar"><label htmlFor="people-search">Szukaj osoby<input id="people-search" type="search" maxLength={200} value={query} onChange={event => { setQuery(event.target.value); setPage(0) }} placeholder="Imię lub nazwisko…" /></label><button className="primary" onClick={onAdd}>Dodaj osobę</button></div>
    <p className="people-sort-note">Kolejność uwzględnia historię wspólnych chwil.</p>
    {loading && <p className="loading-message" role="status">Wczytywanie ludzi…</p>}
    {error && <p className="field-error" role="alert">{error} <button className="text-button" onClick={() => setRetry(value => value + 1)}>Spróbuj ponownie</button></p>}
    {!loading && !error && result?.content.length === 0 && <div className="card empty-feed"><h3>{query.trim() ? 'Nie znaleziono osób.' : 'Poznaj swój krąg.'}</h3><p>{query.trim() ? 'Spróbuj innego imienia lub nazwiska.' : 'Dodaj pierwszą osobę do swojego Meetera.'}</p></div>}
    {result && <p className="feed-label" role="status">TWOI LUDZIE <span>{result.totalElements}</span></p>}
    <div className="people-browser-grid">{result?.content.map(({ person, visitCount, publicQuote }) => <article className="card person-browser-card" key={person.id} onClick={event => {
      if (event.target.closest('a, button') || window.getSelection()?.toString()) return
      openPerson({ person, visitCount, publicQuote })
    }}>
      <div className="person"><PersonAvatar person={person} /><div><h3><button type="button" className="person-open" aria-haspopup="dialog" onClick={() => openPerson({ person, visitCount, publicQuote })}>{person.fullName}</button></h3>{person.demo && <small className="sample-person">przykład</small>}</div></div>
      <p className="person-visit-count"><strong>{visitCount}</strong> {visitCount === 1 ? 'wizyta' : visitCount % 10 >= 2 && visitCount % 10 <= 4 && (visitCount % 100 < 12 || visitCount % 100 > 14) ? 'wizyty' : 'wizyt'}</p>
      {(person.clique || person.livesIn) && <p>{[person.clique?.cliqueName, person.livesIn].filter(Boolean).join(' · ')}</p>}
      {publicQuote && <div className="person-public-quote"><small>PUBLICZNY CYTAT</small><QuoteContent quote={publicQuote.quote} tabIndex={0} aria-label={`Publiczny cytat: ${person.fullName}`} /></div>}
    </article>)}</div>
    {result && (page > 0 || result.hasNext) && <div className="event-photo-pages"><button disabled={loading || page === 0} onClick={() => setPage(value => value - 1)}>Poprzednia strona</button><span>Strona {page + 1}</span><button disabled={loading || !result.hasNext} onClick={() => setPage(value => value + 1)}>Następna strona</button></div>}
    <dialog ref={personDialog} className="login-dialog person-detail-dialog" aria-labelledby="person-detail-title" onClose={() => setSelected(null)} onClick={event => {
      if (event.target !== event.currentTarget) return
      const bounds = event.currentTarget.getBoundingClientRect()
      if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) personDialog.current.close()
    }}>
      <button type="button" className="close-dialog" aria-label="Zamknij szczegóły osoby" onClick={() => personDialog.current.close()} autoFocus>×</button>
      {selected && <PersonProfile key={selected.person.id} entry={selected} onPersonChange={person => {
        setSelected(current => ({ ...current, person }))
        setResult(current => current && ({ ...current, content: current.content.map(item => item.person.id === person.id ? { ...item, person } : item) }))
      }} />}
    </dialog>
  </section>
}
