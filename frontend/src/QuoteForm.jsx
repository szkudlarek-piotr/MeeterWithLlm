import { useState } from 'react'
import EntityPicker from './EntityPicker.jsx'
import PersonAvatar from './PersonAvatar.jsx'
import { t } from './i18n.js'

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
    if (text.length < 4 || text.length > 10240) { setError(t('Cytat powinien mieć od 4 do 10240 znaków.')); return }
    if (!author.length) { setError(t('Wybierz autora cytatu.')); return }
    setError(''); setSaving(true)
    try { await onSave({ quote: text, authorId: author[0]?.id ?? null, isPublic }) }
    catch (error) { setError(error.message) }
    finally { setSaving(false) }
  }
  return <form className="interaction-form quote-form" onSubmit={submit}>
    <p className="form-intro">{t('Zachowaj słowa, do których chcesz wracać.')}</p>
    <fieldset disabled={saving}>
      <label htmlFor="quote-text">{t('Treść cytatu')} *</label>
      <textarea autoFocus id="quote-text" rows={6} required minLength={4} maxLength={10240} value={quote} onChange={event => setQuote(event.target.value)} placeholder={t('Jakie słowa chcesz zapamiętać?')} aria-describedby="quote-length" />
      <p id="quote-length" className="field-hint">{t('Od 4 do 10240 znaków')} · {quote.length}/10240</p>
      {author.length ? <div className="quote-author-selection">
        <p id="quote-author-label">{t('Autor cytatu')} *</p>
        <div role="group" aria-labelledby="quote-author-label">
          <span className="selected-person"><PersonAvatar person={author[0]} /><strong>{author[0].fullName}</strong></span>
          <button type="button" onClick={() => setAuthor([])}>{t('Zmień autora')}</button>
        </div>
      </div> : <EntityPicker label={t('Autor cytatu')} source="quotes/authors" required value={author} onChange={setAuthor} />}
      <p className="field-hint">{t('Wybierz jednego autora z katalogu. Możesz też wybrać siebie.')}</p>
      <label className="checkbox-label"><input type="checkbox" checked={isPublic} onChange={event => setPublic(event.target.checked)} aria-describedby="quote-visibility" /> {t('Cytat publiczny')}</label>
      <p id="quote-visibility" className="field-hint">{t('Publiczny cytat zobaczą wszyscy zalogowani użytkownicy. Prywatny zobaczy jego autor i administrator.')}</p>
      {error && <p className="field-error" role="alert">{error}</p>}
      <button className="primary submit" type="submit">{saving ? t('Zapisywanie…') : t('Dodaj cytat →')}</button>
    </fieldset>
  </form>
}
