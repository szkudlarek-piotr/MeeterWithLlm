import { useEffect, useState } from 'react'
import { api } from './api.js'
import { t, tf, dateLocale } from './i18n.js'

const activityTypes = [
  { value: 'RUN', label: 'Bieg' },
  { value: 'WALK', label: 'Spacer' },
  { value: 'BIKE', label: 'Rower' },
  { value: 'GYM', label: 'Siłownia' },
  { value: 'SWIM', label: 'Pływanie' },
  { value: 'OTHER', label: 'Inne' },
]
const typeLabel = value => t((activityTypes.find(item => item.value === value) ?? activityTypes[5]).label)
const todayIso = () => new Date().toLocaleDateString('sv-SE')
const emptyForm = () => ({ type: 'GYM', date: todayIso(), durationMinutes: '', description: '' })
const formatDate = iso => new Date(`${iso}T00:00:00`).toLocaleDateString(dateLocale(), { day: 'numeric', month: 'short', year: 'numeric' })

export default function PhysicalActivityPanel() {
  const [items, setItems] = useState([])
  const [summary, setSummary] = useState(null)
  const [form, setForm] = useState(emptyForm)
  const [editingId, setEditingId] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [loadError, setLoadError] = useState('')
  const [reload, setReload] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([
      api('/physical-activities', { signal: controller.signal }),
      api('/physical-activities/summary', { signal: controller.signal }),
    ])
      .then(([list, stats]) => { setItems(list); setSummary(stats); setLoadError('') })
      .catch(err => { if (!controller.signal.aborted) setLoadError(err.message) })
    return () => controller.abort()
  }, [reload])

  function edit(item) {
    setEditingId(item.id)
    setForm({ type: item.type, date: item.date, durationMinutes: String(item.durationMinutes), description: item.description ?? '' })
    setError('')
  }

  function cancelEdit() {
    setEditingId(null)
    setForm(emptyForm())
    setError('')
  }

  async function submit(event) {
    event.preventDefault()
    const minutes = Number(form.durationMinutes)
    if (!Number.isInteger(minutes) || minutes < 1 || minutes > 1440) {
      setError(t('Podaj czas trwania od 1 do 1440 minut.'))
      return
    }
    const body = { type: form.type, date: form.date, durationMinutes: minutes, description: form.description.trim() || null }
    setBusy(true)
    setError('')
    try {
      if (editingId) await api(`/physical-activities/${editingId}`, { method: 'PUT', body })
      else await api('/physical-activities', { body })
      cancelEdit()
      setReload(value => value + 1)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  async function remove(item) {
    if (!window.confirm(t('Usunąć ten wpis aktywności?'))) return
    setError('')
    try {
      await api(`/physical-activities/${item.id}`, { method: 'DELETE' })
      if (editingId === item.id) cancelEdit()
      setReload(value => value + 1)
    } catch (err) {
      setError(err.message)
    }
  }

  return <section className="card physical-activity">
    <h3>{t('Aktywność fizyczna')}</h3>
    {loadError && <p className="field-error" role="alert">{loadError}</p>}
    {summary && <div className="activity-summary" aria-label={t('Podsumowanie aktywności')}>
      <p><strong>{summary.weekCount}</strong> {t('treningów w tym tygodniu')}</p>
      <p><strong>{summary.weekMinutes}</strong> {t('minut w tym tygodniu')}</p>
      <p><strong>{summary.streakDays}</strong> {t('dni z rzędu')}</p>
    </div>}

    <form className="card interaction-form" onSubmit={submit}>
      <h4>{editingId ? t('Edytuj wpis') : t('Dodaj aktywność')}</h4>
      <fieldset disabled={busy}>
        <label htmlFor="activity-type">{t('Typ aktywności')}</label>
        <select id="activity-type" value={form.type} onChange={event => setForm({ ...form, type: event.target.value })}>
          {activityTypes.map(item => <option key={item.value} value={item.value}>{t(item.label)}</option>)}
        </select>
        <label htmlFor="activity-date">{t('Data')}</label>
        <input id="activity-date" type="date" required max={todayIso()} value={form.date} onChange={event => setForm({ ...form, date: event.target.value })} />
        <label htmlFor="activity-minutes">{t('Czas trwania (minuty)')}</label>
        <input id="activity-minutes" type="number" required min={1} max={1440} value={form.durationMinutes} onChange={event => setForm({ ...form, durationMinutes: event.target.value })} />
        <label htmlFor="activity-description">{t('Opis (opcjonalnie)')}</label>
        <textarea id="activity-description" maxLength={1000} rows={3} value={form.description} placeholder={t('Np. hantle 12 kg × 10, pompki 30')} onChange={event => setForm({ ...form, description: event.target.value })} />
        {error && <p className="field-error" role="alert">{error}</p>}
        <button className="primary submit" disabled={busy}>{busy ? t('Zapisywanie…') : editingId ? t('Zapisz zmiany') : t('Dodaj wpis')}</button>
        {editingId && <button type="button" className="text-button" onClick={cancelEdit}>{t('Anuluj edycję')}</button>}
      </fieldset>
    </form>

    <div className="activity-list">
      {items.length === 0 && !loadError && <p>{t('Nie ma jeszcze wpisów. Zapisz pierwszy trening.')}</p>}
      {items.map(item => <div className="activity-row" key={item.id}>
        <div>
          <strong>{typeLabel(item.type)}</strong>
          <small>{formatDate(item.date)} · {tf('{minutes} min', { minutes: item.durationMinutes })}</small>
          {item.description && <p>{item.description}</p>}
        </div>
        <div>
          <button className="text-button" onClick={() => edit(item)}>{t('Edytuj')}</button>
          <button className="text-button" onClick={() => remove(item)}>{t('Usuń')}</button>
        </div>
      </div>)}
    </div>
  </section>
}
