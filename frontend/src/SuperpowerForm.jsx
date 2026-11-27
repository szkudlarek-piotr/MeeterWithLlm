import { useState } from 'react'
import { t } from './i18n.js'

export default function SuperpowerForm({ onSave }) {
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  async function submit(event) {
    event.preventDefault()
    const superpowerName = String(new FormData(event.currentTarget).get('superpowerName')).trim()
    if (!superpowerName) { setError(t('Podaj nazwę supermocy.')); return }
    setSaving(true)
    setError('')
    try { await onSave({ superpowerName }) }
    catch (error) { setError(error.message) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}>
    <p className="form-intro">{t('Nazwij moc, którą będzie można przypisać swoim ludziom.')}</p>
    <label htmlFor="superpower-name">{t('Nazwa supermocy')}</label>
    <input autoFocus id="superpower-name" name="superpowerName" required maxLength={255} pattern=".*\S.*" placeholder={t('Np. latanie, czytanie w myślach')} aria-invalid={!!error} onChange={() => setError('')} />
    <small className="field-hint">{t('Do 255 znaków.')}</small>
    {error && <p className="field-error" role="alert">{error}</p>}
    <p className="demo-note">{t('Supermoc zostanie zapisana w bazie developerskiej.')}</p>
    <button className="primary submit" disabled={saving}>{saving ? t('Zapisywanie…') : t('Dodaj supermoc →')}</button>
  </form>
}
