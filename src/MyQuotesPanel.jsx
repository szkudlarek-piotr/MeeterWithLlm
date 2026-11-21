import { useEffect, useState } from 'react'
import { DndContext, KeyboardSensor, PointerSensor, useDraggable, useDroppable, useSensor, useSensors } from '@dnd-kit/core'
import { api } from './api.js'
import { t } from './i18n.js'
import QuoteContent from './QuoteContent.jsx'

const columns = [
  { key: 'public', isPublic: true, title: 'myQuotes.public' },
  { key: 'private', isPublic: false, title: 'myQuotes.private' },
]

function QuoteColumn({ column, count, children }) {
  const { isOver, setNodeRef } = useDroppable({ id: column.key })
  return <section ref={setNodeRef} className={`my-quotes-column${isOver ? ' is-drop-target' : ''}`} aria-labelledby={`my-quotes-${column.key}`}>
    <h4 id={`my-quotes-${column.key}`}>{t(column.title)} <small>{count}</small></h4>
    {children}
  </section>
}

function DraggableQuote({ quote, busy }) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({ id: quote.uuid, disabled: busy })
  const style = transform ? { transform: `translate3d(${transform.x}px, ${transform.y}px, 0)`, zIndex: 10 } : undefined
  return <li ref={setNodeRef} style={style} className={`my-quote${isDragging ? ' is-dragging' : ''}`} {...listeners} {...attributes}>
    <QuoteContent quote={quote.quote} />
  </li>
}

export default function MyQuotesPanel() {
  const [quotes, setQuotes] = useState(null)
  const [error, setError] = useState('')
  const [busyUuid, setBusyUuid] = useState(null)
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor),
  )

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

  function handleDragEnd({ active, over }) {
    if (!over || !quotes) return
    const quote = quotes.find(item => item.uuid === active.id)
    const column = columns.find(item => item.key === over.id)
    if (quote && column) move(quote, column.isPublic)
  }

  return <section className="my-quotes" aria-label={t('myQuotes.title')}>
    {error && <p className="field-error" role="alert">{error}</p>}
    {!quotes && !error && <p role="status">{t('myQuotes.loading')}</p>}
    {quotes && quotes.length === 0 && <p className="field-hint">{t('myQuotes.empty')}</p>}
    {quotes && <DndContext sensors={sensors} onDragEnd={handleDragEnd}>
      <div className="my-quotes-board">
        {columns.map(column => {
          const items = quotes.filter(quote => quote.isPublic === column.isPublic)
          return <QuoteColumn key={column.key} column={column} count={items.length}>
            <ul>
              {items.length === 0 && <li className="my-quotes-empty">{t('myQuotes.dropHere')}</li>}
              {items.map(quote => <DraggableQuote key={quote.uuid} quote={quote} busy={busyUuid === quote.uuid} />)}
            </ul>
          </QuoteColumn>
        })}
      </div>
    </DndContext>}
  </section>
}
