import { useState } from 'react'
import { api } from './api.js'
import { t } from './i18n.js'

export default function AdminNewsForm() {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [published, setPublished] = useState(false)

  async function submit(event) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    setBusy(true)
    setError('')
    setPublished(false)
    try {
      await api('/news', { body: { title: data.get('title').trim(), content: data.get('content').trim() } })
      form.reset()
      setPublished(true)
    } catch (error) {
      setError(error.message)
    } finally {
      setBusy(false)
    }
  }

  return <section className="card admin-photo-import" aria-labelledby="admin-news-title">
    <h3 id="admin-news-title">{t('Dodaj aktualność')}</h3>
    <form onSubmit={submit}>
      <label htmlFor="news-title">{t('Tytuł')}</label>
      <input id="news-title" name="title" maxLength={160} required disabled={busy} />
      <label htmlFor="news-content">{t('Treść')}</label>
      <textarea id="news-content" name="content" rows={6} maxLength={10240} required disabled={busy} />
      <button className="primary" disabled={busy}>{busy ? t('Publikowanie…') : t('Opublikuj')}</button>
    </form>
    {error && <p className="field-error" role="alert">{error}</p>}
    {published && <p role="status">{t('Aktualność została opublikowana.')}</p>}
  </section>
}
