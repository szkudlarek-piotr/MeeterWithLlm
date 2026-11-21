import { useState } from 'react'

export default function SuperpowerForm({ onSave }) {
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  async function submit(event) {
    event.preventDefault()
    const superpowerName = String(new FormData(event.currentTarget).get('superpowerName')).trim()
    if (!superpowerName) { setError('Podaj nazwę supermocy.'); return }
    setSaving(true)
    setError('')
    try { await onSave({ superpowerName }) }
    catch (error) { setError(error.message) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}>
    <p className="form-intro">Nazwij moc, którą będzie można przypisać swoim ludziom.</p>
    <label htmlFor="superpower-name">Nazwa supermocy</label>
    <input autoFocus id="superpower-name" name="superpowerName" required maxLength={255} pattern=".*\S.*" placeholder="Np. latanie, czytanie w myślach" aria-invalid={!!error} onChange={() => setError('')} />
    <small className="field-hint">Do 255 znaków.</small>
    {error && <p className="field-error" role="alert">{error}</p>}
    <p className="demo-note">Supermoc zostanie zapisana w bazie developerskiej.</p>
    <button className="primary submit" disabled={saving}>{saving ? 'Zapisywanie…' : 'Dodaj supermoc →'}</button>
  </form>
}
