import { RecommendationPosition } from './styles/ParticipantRecommendations.js'
import { useEffect, useId, useRef, useState } from 'react'
import { api } from './api.js'
import PersonAvatar from './PersonAvatar.jsx'

const algorithms = { logistic: 'Regresja logistyczna', extra_trees: 'Extra Trees', boosting: 'Boosting' }
function modelLabel(model) {
  const algorithm = model.id.replace(/^(context|guests)_/, '')
  return `${algorithms[algorithm] || algorithm} · ${model.mode === 'guests' ? 'nazwa, miejsce i goście' : 'nazwa i miejsce'}${model.recommended ? ' · zalecany' : ''}`
}

export default function ParticipantRecommendations({ name, startDate, placeText = '', places = [], guests = [], onSelect, interactionId }) {
  const id = useId()
  const [models, setModels] = useState([])
  const [model, setModel] = useState(null)
  const [listError, setListError] = useState('')
  const [listLoading, setListLoading] = useState(true)
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
    setListLoading(true); setListError('')
    api('/participant-models', { signal: controller.signal })
      .then(items => {
        if (controller.signal.aborted) return
        setModels(items)
        setModel(previous => previous === '' || items.some(item => item.id === previous) ? previous
          : (items.find(item => item.recommended && item.mode === 'guests') || items.find(item => item.mode === 'guests') || items[0])?.id || '')
      })
      .catch(error => { if (!controller.signal.aborted) setListError(error.message) })
      .finally(() => { if (!controller.signal.aborted) setListLoading(false) })
    return () => controller.abort()
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

  return <section className="participant-recommendations" aria-label="Eksperymentalne podpowiedzi uczestników">
    <label htmlFor={id}>Model podpowiedzi <small>· eksperyment</small></label>
    <div className="recommendation-controls">
      <select id={id} value={model || ''} disabled={listLoading} onChange={event => { setModel(event.target.value); setResult(null) }}>
        <option value="">{listLoading ? 'Sprawdzanie modeli…' : 'Wyłączone — wybieraj osoby ręcznie'}</option>
        {models.map(item => <option key={item.id} value={item.id}>{modelLabel(item)}</option>)}
      </select>
      <button type="button" disabled={!model || body.name.length < 2 || busy} onClick={suggest}>{busy ? 'Szukanie propozycji…' : 'Zaproponuj uczestników'}</button>
    </div>
    {model && <p className="field-hint">{model.startsWith('guests_') ? 'Uwzględnia nazwę, miejsca i już wybrane osoby.' : 'Uwzględnia nazwę i miejsca.'} Propozycje odświeżają się automatycznie po zmianie formularza. Kliknij osobę, aby ją dodać.</p>}
    {!listLoading && (listError || !models.length) && <p className="field-hint" role="status">{listError || 'Brak modeli dla Twojego konta. Sprawdź wytrenowane pliki i środowisko Python, a następnie uruchom ponownie backend.'} <button type="button" className="text-button" onClick={() => setReload(value => value + 1)}>Sprawdź ponownie</button></p>}
    <div aria-live="polite" aria-busy={busy}>
      {current?.error && <p className="field-error" role="alert">{current.error}</p>}
      {current?.people?.length === 0 && <p className="field-hint">Brak kolejnych propozycji dla tego zestawu.</p>}
      {current?.people?.length > 0 && <div className="recommendation-people">{current.people.map((person, index) => <button type="button" key={person.id} onClick={() => onSelect(person)} aria-label={`Dodaj: ${person.fullName}`}><RecommendationPosition>{index + 1}</RecommendationPosition><PersonAvatar person={person} /><span>{person.fullName}</span><span aria-hidden="true">＋</span></button>)}</div>}
    </div>
  </section>
}
