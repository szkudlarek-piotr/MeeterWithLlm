import { useEffect, useRef, useState } from 'react'
import { api } from './api.js'
import PersonAvatar from './PersonAvatar.jsx'
import PersonProfile from './PersonProfile.jsx'
import { t, tf } from './i18n.js'

function CliquePhoto({ clique }) {
  const [failed, setFailed] = useState(false)
  useEffect(() => setFailed(false), [clique.photoUrl])
  return <div className="clique-cover">
    {!failed && clique.photoUrl ? <img src={clique.photoUrl} alt={tf('Klika {name}', { name: clique.cliqueName })} loading="lazy" onError={() => setFailed(true)} /> : <div className="clique-cover-placeholder" role="img" aria-label={t('Brak zdjęcia kliki')}><span aria-hidden="true">◌</span></div>}
  </div>
}

export default function CliquesPanel({ version, onAdd, publicQuotesOnly, onTogglePublicQuotes }) {
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const [selected, setSelected] = useState(null)
  const personDialog = useRef(null)
  function openPerson(person) {
    setSelected({ person })
    personDialog.current.showModal()
  }
  useEffect(() => {
    const controller = new AbortController()
    setLoading(true); setError(''); setResult(null)
    api(`/cliques/browse?page=${page}&size=12`, { signal: controller.signal })
      .then(value => { if (!controller.signal.aborted) setResult(value) })
      .catch(error => { if (!controller.signal.aborted) setError(error.message) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [page, version, retry])
  return <section aria-label={t('Przeglądaj kliki')}>
    <div className="people-toolbar"><p className="people-sort-note">{t('Kolejność uwzględnia historię wspólnych chwil. Członkowie w kolejności z widoku ludzi.')}</p><button className="primary" onClick={onAdd}>{t('Dodaj klikę')}</button></div>
    {loading && <p role="status">{t('Wczytywanie klik…')}</p>}
    {error && <p className="field-error" role="alert">{error} <button className="text-button" onClick={() => setRetry(value => value + 1)}>{t('Spróbuj ponownie')}</button></p>}
    {!loading && !error && result?.content.length === 0 && <div className="card empty-feed"><h3>{t('Twój krąg, Twoje kliki.')}</h3><p>{t('Dodaj pierwszą klikę, aby zebrać swoich ludzi w jednym miejscu.')}</p></div>}
    <div className="cliques-grid">{result?.content.map(clique => <article className="card clique-card" key={clique.id}>
      <div className="clique-card-heading"><CliquePhoto clique={clique} /><h3>{clique.cliqueName}</h3></div>
      <div className="clique-card-content">
        {clique.members.length ? <ul className="clique-members" aria-label={tf('Członkowie kliki {name}', { name: clique.cliqueName })}>{clique.members.map(person => <li key={person.id}><button type="button" className="clique-member-open" aria-label={tf('Otwórz profil: {name}', { name: person.fullName })} aria-haspopup="dialog" onClick={() => openPerson(person)}><PersonAvatar person={person} /></button></li>)}</ul> : <p className="people-sort-note">{t('Brak osób do wyświetlenia.')}</p>}
      </div>
    </article>)}</div>
    {result && (page > 0 || result.hasNext) && <div className="event-photo-pages"><button disabled={loading || page === 0} onClick={() => setPage(value => value - 1)}>{t('Poprzednia strona')}</button><span>{t('Strona')} {page + 1}</span><button disabled={loading || !result.hasNext} onClick={() => setPage(value => value + 1)}>{t('Następna strona')}</button></div>}
    <dialog ref={personDialog} className="login-dialog person-detail-dialog" aria-labelledby="person-detail-title" onClose={() => setSelected(null)} onClick={event => {
      if (event.target !== event.currentTarget) return
      const bounds = event.currentTarget.getBoundingClientRect()
      if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) personDialog.current.close()
    }}>
      <button type="button" className="close-dialog" aria-label={t('Zamknij szczegóły osoby')} onClick={() => personDialog.current.close()} autoFocus>×</button>
      {selected && <PersonProfile key={selected.person.id} entry={selected} publicQuotesOnly={publicQuotesOnly} onTogglePublicQuotes={onTogglePublicQuotes} onPersonChange={person => {
        setSelected(current => ({ ...current, person }))
        setResult(current => current && ({ ...current, content: current.content.map(clique => ({ ...clique, members: clique.members.map(member => member.id === person.id ? person : member) })) }))
      }} />}
    </dialog>
  </section>
}
