import { useState } from 'react'
import { api } from './api.js'
import { t, tf } from './i18n.js'

const labels = { MEETING: t('Spotkania'), VISIT: t('Wizyty'), EVENT: t('Wydarzenia'), TRIP: t('Podróże'), WEDDING: t('Wesela z zaproszeniem') }
export default function ParticipationBackfillPanel({ onChanged }) {
  const [preview, setPreview] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState(null)
  async function load() {
    setBusy(true); setError(''); setResult(null); setPreview(null)
    try { setPreview(await api('/admin/participation-backfill')) }
    catch (error) { setError(error.message) } finally { setBusy(false) }
  }
  async function apply() {
    setBusy(true); setError(''); setResult(null)
    try {
      setResult(await api('/admin/participation-backfill', { body: {} }))
      setPreview(null); onChanged()
    } catch (error) { setError(error.message) } finally { setBusy(false) }
  }
  const total = preview ? Object.values(preview.missing).reduce((sum, count) => sum + count, 0) : 0
  return <section className="card admin-photo-import" aria-labelledby="participation-backfill-title">
    <h3 id="participation-backfill-title">{t('Uzupełnij mój udział w interakcjach')}</h3>
    <p>{t('Dodaj osobę powiązaną z Twoim kontem do wszystkich spotkań, wizyt, wydarzeń i podróży, w których jej brakuje — również zapisanych przez inne konta.')}</p>
    <p>{t('Wesela zostaną uwzględnione wyłącznie z zaznaczonym „Byłem / byłam zaproszony(-a)”. Wspólne mieszkanie jest pomijane. Operacja obejmuje także wcześniej usunięty udział.')}</p>
    <button className="text-button" disabled={busy} onClick={load}>{t('Sprawdź brakujące powiązania')}</button>
    {preview && <div><ul>{Object.entries(preview.missing).map(([type, count]) => <li key={type}>{labels[type]}: {count}</li>)}</ul>
      {total === 0 ? <p>{t('Nie ma brakujących powiązań.')}</p> : <button className="primary" disabled={busy} onClick={apply}>{tf('Dodaj mnie do {total} interakcji', { total })}</button>}
    </div>}
    {busy && <p role="status">{t('Przetwarzanie…')}</p>}
    {error && <p className="field-error" role="alert">{error}</p>}
    {result && <div role="status"><p>{t('Dodano powiązania:')}</p><ul>{Object.entries(result.added).map(([type, count]) => <li key={type}>{labels[type]}: {count}</li>)}</ul><p>{tf('Pominięto równolegle zmienione rekordy: {skipped}. Istniejące powiązania zostały zachowane.', { skipped: result.skipped })}</p></div>}
  </section>
}
