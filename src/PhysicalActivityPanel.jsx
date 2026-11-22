import { useEffect, useState } from 'react'
import { api } from './api.js'
import { t, dateLocale } from './i18n.js'

const todayIso = () => new Date().toLocaleDateString('sv-SE')
const emptyForm = () => ({ category: '', date: todayIso(), description: '' })
const formatDate = iso => new Date(`${iso}T00:00:00`).toLocaleDateString(dateLocale(), { day: 'numeric', month: 'short', year: 'numeric' })

export default function PhysicalActivityPanel() {
  const [items, setItems] = useState([])
  const [summary, setSummary] = useState(null)
  const [categories, setCategories] = useState([])
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
      api('/physical-activities/categories', { signal: controller.signal }),
    ])
      .then(([list, stats, names]) => { setItems(list); setSummary(stats); setCategories(names); setLoadError('') })
      .catch(err => { if (!controller.signal.aborted) setLoadError(err.message) })
    return () => controller.abort()
  }, [reload])

  function edit(item) {
    setEditingId(item.id)
    setForm({ category: item.category, date: item.date, description: item.description ?? '' })
    setError('')
  }

  function cancelEdit() {
    setEditingId(null)
    setForm(emptyForm())
    setError('')
  }

  async function submit(event) {
    event.preventDefault()
    const category = form.category.trim().replace(/\s+/g, ' ')
    if (!category) {
      setError(t('Podaj kategorię aktywności.'))
      return
    }
    if (category.length > 64) {
      setError(t('Kategoria może mieć najwyżej 64 znaki.'))
      return
    }
    const body = { category, date: form.date, description: form.description.trim() || null }
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
      <p><strong>{summary.streakDays}</strong> {t('dni z rzędu')}</p>
    </div>}

    <form className="card interaction-form" onSubmit={submit}>
      <h4>{editingId ? t('Edytuj wpis') : t('Dodaj aktywność')}</h4>
      <fieldset disabled={busy}>
        <label htmlFor="activity-category">{t('Kategoria aktywności')}</label>
        <input id="activity-category" list="activity-category-options" autoComplete="off" required maxLength={64}
          value={form.category} placeholder={t('Np. siłownia, bieg, joga')}
          onChange={event => setForm({ ...form, category: event.target.value })} />
        <datalist id="activity-category-options">
          {categories.map(name => <option key={name} value={name} />)}
        </datalist>
        <label htmlFor="activity-date">{t('Data')}</label>
        <input id="activity-date" type="date" required max={todayIso()} value={form.date} onChange={event => setForm({ ...form, date: event.target.value })} />
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
          <strong>{item.category}</strong>
          <small>{formatDate(item.date)}</small>
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
