import { RecommendationPosition } from './styles/ParticipantRecommendations.js'
import { useEffect, useId, useRef, useState } from 'react'
import { api } from './api.js'
import PersonAvatar from './PersonAvatar.jsx'
import { t } from './i18n.js'

const algorithms = { logistic: 'Regresja logistyczna', extra_trees: 'Extra Trees', boosting: 'Boosting' }
// The model service loads its weights on start-up, so the first list request can fail or come back empty.
// Retry with backoff instead of leaving the picker stuck on "no models" until the user clicks.
const LIST_RETRY_DELAY_MS = 1000
const LIST_MAX_ATTEMPTS = 6

function modelLabel(model) {
  const algorithm = model.id.replace(/^(context|guests)_/, '')
  const mode = model.mode === 'guests' ? t('recommendations.modeGuests') : t('recommendations.modeContext')
  return `${algorithms[algorithm] || algorithm} · ${mode}${model.recommended ? ` · ${t('recommendations.recommended')}` : ''}`
}

export default function ParticipantRecommendations({ name, startDate, placeText = '', places = [], guests = [], onSelect, interactionId }) {
  const id = useId()
  const [models, setModels] = useState([])
  const [model, setModel] = useState(null)
  const [listError, setListError] = useState('')
  const [listLoading, setListLoading] = useState(true)
  const [listRetrying, setListRetrying] = useState(false)
  const [reload, setReload] = useState(0)
  const [result, setResult] = useState(null)
  const [busyKey, setBusyKey] = useState(null)
  const request = useRef(null)
  const guestIds = [...new Set(guests.map(person => person.id))].sort()
  const placeIds = [...new Set(places.map(place => place.id))]
  const body = { model, name: name.trim(), startDate: startDate?.slice(0, 10) || undefined, placeText: placeText.trim(), placeIds, guestIds, ...(interactionId ? { interactionId } : {}) }
  const key = JSON.stringify(body)
  const current = result?.key === key ? result : null
  const busy = busyKey === key

  useEffect(() => {
    const controller = new AbortController()
    let attempt = 0
    let timer
    setListLoading(true); setListError(''); setListRetrying(false)

    const scheduleRetry = () => {
      attempt += 1
      setListRetrying(true)
      timer = setTimeout(load, LIST_RETRY_DELAY_MS * 2 ** (attempt - 1))
    }
    const finish = (items, error = '') => {
      setModels(items)
      setListError(error)
      setListRetrying(false)
      setListLoading(false)
      setModel(previous => previous === '' || items.some(item => item.id === previous) ? previous
        : (items.find(item => item.recommended && item.mode === 'guests') || items.find(item => item.mode === 'guests') || items[0])?.id || '')
    }
    function load() {
      api('/participant-models', { signal: controller.signal })
        .then(items => {
          if (controller.signal.aborted) return
          if (items.length === 0 && attempt < LIST_MAX_ATTEMPTS - 1) return scheduleRetry()
          finish(items)
        })
        .catch(error => {
          if (controller.signal.aborted) return
          if (attempt < LIST_MAX_ATTEMPTS - 1) return scheduleRetry()
          finish([], error.message)
        })
    }
    load()
    return () => { controller.abort(); clearTimeout(timer) }
  }, [reload])

  useEffect(() => {
    setBusyKey(null)
    return () => request.current?.abort()
  }, [key])

  useEffect(() => {
    if (!model || body.name.length < 2 || listLoading) return
    const timer = setTimeout(() => suggest(), 700)
    return () => clearTimeout(timer)
  }, [key, listLoading])

  async function suggest() {
    request.current?.abort()
    const controller = new AbortController()
    request.current = controller
    setBusyKey(key); setResult(null)
    try {
      const people = await api('/participant-models/suggest', { body, signal: controller.signal })
      if (!controller.signal.aborted) setResult({ key, people })
    } catch (error) {
      if (!controller.signal.aborted) setResult({ key, error: error.message })
    } finally {
      if (!controller.signal.aborted) setBusyKey(null)
    }
  }

  return <section className="participant-recommendations" aria-label={t('recommendations.section')}>
    <label htmlFor={id}>{t('recommendations.label')} <small>· {t('recommendations.experimental')}</small></label>
    <div className="recommendation-controls">
      <select id={id} value={model || ''} disabled={listLoading} onChange={event => { setModel(event.target.value); setResult(null) }}>
        <option value="">{listLoading ? t('recommendations.loading') : t('recommendations.disabled')}</option>
        {models.map(item => <option key={item.id} value={item.id}>{modelLabel(item)}</option>)}
      </select>
      <button type="button" disabled={!model || body.name.length < 2 || busy} onClick={suggest}>{busy ? t('recommendations.suggestBusy') : t('recommendations.suggest')}</button>
    </div>
    {model && <p className="field-hint">{model.startsWith('guests_') ? t('recommendations.hintGuests') : t('recommendations.hintContext')} {t('recommendations.hintAuto')}</p>}
    {listRetrying && <p className="field-hint" role="status">{t('recommendations.retrying')}</p>}
    {!listLoading && !listRetrying && (listError || !models.length) && <p className="field-hint" role="status">{listError || t('recommendations.noModels')} <button type="button" className="text-button" onClick={() => setReload(value => value + 1)}>{t('recommendations.recheck')}</button></p>}
    <div aria-live="polite" aria-busy={busy}>
      {current?.error && <p className="field-error" role="alert">{current.error}</p>}
      {current?.people?.length === 0 && <p className="field-hint">{t('recommendations.noMore')}</p>}
      {current?.people?.length > 0 && <div className="recommendation-people">{current.people.map((person, index) => <button type="button" key={person.id} onClick={() => onSelect(person)} aria-label={`${t('recommendations.add')}: ${person.fullName}`}><RecommendationPosition>{index + 1}</RecommendationPosition><PersonAvatar person={person} /><span>{person.fullName}</span><span aria-hidden="true">＋</span></button>)}</div>}
    </div>
  </section>
}
