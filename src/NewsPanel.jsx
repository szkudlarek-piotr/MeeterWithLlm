import { useEffect, useState } from 'react'
import { api } from './api.js'

export default function NewsPanel() {
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    setLoading(true); setError(''); setResult(null)
    api(`/news?page=${page}&size=10`, { signal: controller.signal })
      .then(setResult).catch(failure => { if (!controller.signal.aborted) setError(failure.message) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [page, retry])
  return <section aria-label="Nowości w Meeterze">
    {loading && <p role="status" className="loading-message">Wczytywanie aktualności…</p>}
    {error && <p role="alert" className="field-error">{error} <button className="text-button" onClick={() => setRetry(value => value + 1)}>Spróbuj ponownie</button></p>}
    {!loading && !error && result?.content.length === 0 && <div className="card empty-feed"><h3>Jesteś na bieżąco.</h3><p>Tutaj pojawią się informacje o nowych funkcjach i zmianach w Meeterze.</p></div>}
    {result?.content.map(item => <article className="card feed-card" key={item.id}>
      <div className="entry-meta"><span className="entry-icon" aria-hidden="true">✧</span><div><strong>Nowości w Meeterze</strong><time dateTime={item.publishedAt}>{new Date(item.publishedAt).toLocaleDateString('pl-PL', { day: 'numeric', month: 'long', year: 'numeric' })}</time></div></div>
      <h3>{item.title}</h3><p className="news-content">{item.content}</p>
    </article>)}
    {result && (page > 0 || result.hasNext) && <div className="event-photo-pages"><button disabled={loading || page === 0} onClick={() => setPage(value => value - 1)}>Nowsze wpisy</button><span>Strona {page + 1}</span><button disabled={loading || !result.hasNext} onClick={() => setPage(value => value + 1)}>Starsze wpisy</button></div>}
  </section>
}
