import { useEffect, useState } from 'react'
import { api } from './api.js'
import { t } from './i18n.js'
import QuoteContent from './QuoteContent.jsx'

const columns = [
  { key: 'public', isPublic: true, title: 'myQuotes.public' },
  { key: 'private', isPublic: false, title: 'myQuotes.private' },
]

export default function MyQuotesPanel() {
  const [quotes, setQuotes] = useState(null)
  const [error, setError] = useState('')
  const [busyUuid, setBusyUuid] = useState(null)
  const [dragged, setDragged] = useState(null)

  useEffect(() => {
    const controller = new AbortController()
    api('/quotes/mine?size=200', { signal: controller.signal })
      .then(page => { if (!controller.signal.aborted) setQuotes(page.content) })
      .catch(failure => { if (!controller.signal.aborted) setError(failure.message) })
    return () => controller.abort()
  }, [])

  async function move(quote, isPublic) {
    if (quote.isPublic === isPublic || busyUuid) return
    setBusyUuid(quote.uuid); setError('')
    try {
      const updated = await api(`/quotes/${quote.uuid}/visibility`, { method: 'PATCH', body: { isPublic } })
      setQuotes(list => list.map(item => item.uuid === updated.uuid ? { ...item, isPublic: updated.isPublic } : item))
    } catch (failure) {
      setError(failure.message)
    } finally {
      setBusyUuid(null)
    }
  }

  return <section className="my-quotes" aria-label={t('myQuotes.title')}>
    {error && <p className="field-error" role="alert">{error}</p>}
    {!quotes && !error && <p role="status">{t('myQuotes.loading')}</p>}
    {quotes && quotes.length === 0 && <p className="field-hint">{t('myQuotes.empty')}</p>}
    {quotes && quotes.length > 0 && <div className="my-quotes-board">
      {columns.map(column => {
        const items = quotes.filter(quote => quote.isPublic === column.isPublic)
        return <section key={column.key} className="my-quotes-column" aria-labelledby={`my-quotes-${column.key}`}
          onDragOver={event => event.preventDefault()}
          onDrop={event => { event.preventDefault(); if (dragged) move(dragged, column.isPublic); setDragged(null) }}>
          <h4 id={`my-quotes-${column.key}`}>{t(column.title)} <small>({items.length})</small></h4>
          <ul>
            {items.map(quote => <li key={quote.uuid} draggable={busyUuid !== quote.uuid}
              onDragStart={event => { event.dataTransfer.setData('text/plain', quote.uuid); setDragged(quote) }}
              onDragEnd={() => setDragged(null)}>
              <QuoteContent quote={quote.quote} />
              <button type="button" className="text-button" disabled={busyUuid === quote.uuid} onClick={() => move(quote, !quote.isPublic)}>
                {column.isPublic ? t('myQuotes.makePrivate') : t('myQuotes.makePublic')}
              </button>
            </li>)}
          </ul>
        </section>
      })}
    </div>}
  </section>
}
