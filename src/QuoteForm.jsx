import { useState } from 'react'
import EntityPicker from './EntityPicker.jsx'

export default function QuoteForm({ onSave }) {
  const [quote, setQuote] = useState('')
  const [author, setAuthor] = useState([])
  const [isPublic, setPublic] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  async function submit(event) {
    event.preventDefault()
    if (saving) return
    const text = quote.trim()
    if (text.length < 4 || text.length > 10240) { setError('Cytat powinien mieć od 4 do 10240 znaków.'); return }
    setError(''); setSaving(true)
    try { await onSave({ quote: text, authorId: author[0]?.id ?? null, isPublic }) }
    catch (error) { setError(error.message) }
    finally { setSaving(false) }
  }
  return <form className="interaction-form quote-form" onSubmit={submit}>
    <p className="form-intro">Zachowaj słowa, do których chcesz wracać.</p>
    <fieldset disabled={saving}>
      <label htmlFor="quote-text">Treść cytatu *</label>
      <textarea autoFocus id="quote-text" rows={6} required minLength={4} maxLength={10240} value={quote} onChange={event => setQuote(event.target.value)} placeholder="Jakie słowa chcesz zapamiętać?" aria-describedby="quote-length" />
      <p id="quote-length" className="field-hint">Od 4 do 10240 znaków · {quote.length}/10240</p>
      <EntityPicker label="Autor cytatu (opcjonalnie)" source="people" value={author} onChange={setAuthor} />
      <p className="field-hint">Pozostaw puste, jeśli autor jest nieznany. Możesz też wybrać siebie.</p>
      <label className="checkbox-label"><input type="checkbox" checked={isPublic} onChange={event => setPublic(event.target.checked)} aria-describedby="quote-visibility" /> Cytat publiczny</label>
      <p id="quote-visibility" className="field-hint">Publiczny cytat zobaczą wszyscy zalogowani użytkownicy. Prywatny zobaczy jego autor i administrator. Prywatny cytat bez autora pozostaje dostępny dla Ciebie i administratora.</p>
      {error && <p className="field-error" role="alert">{error}</p>}
      <button className="primary submit" type="submit">{saving ? 'Zapisywanie…' : 'Dodaj cytat →'}</button>
    </fieldset>
  </form>
}
