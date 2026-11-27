import { useState } from 'react'
import { api } from './api.js'
import { t } from './i18n.js'

const statusKeys = { IMPORTED: 'superpowerPhotos.status.IMPORTED', WOULD_IMPORT: 'superpowerPhotos.status.WOULD_IMPORT', SKIPPED: 'superpowerPhotos.status.SKIPPED', FAILED: 'superpowerPhotos.status.FAILED' }

export default function SuperpowerPhotoImportPanel() {
  const [folder, setFolder] = useState('')
  const [dryRun, setDryRun] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [report, setReport] = useState(null)

  async function submit(event) {
    event.preventDefault()
    setBusy(true); setError(''); setReport(null)
    try {
      setReport(await api('/admin/superpowers/photos/import', { body: { folder: folder.trim(), dryRun } }))
    } catch (failure) { setError(failure.message) } finally { setBusy(false) }
  }

  return <section className="card admin-photo-import" aria-labelledby="superpower-import-title">
    <h3 id="superpower-import-title">{t('superpowerPhotos.title')}</h3>
    <p>{t('superpowerPhotos.intro')}</p>
    <p>{t('superpowerPhotos.details')}</p>
    <form onSubmit={submit}>
      <label htmlFor="superpower-photo-folder">{t('superpowerPhotos.folder')}</label>
      <input id="superpower-photo-folder" value={folder} onChange={event => setFolder(event.target.value)} placeholder="C:\Zdjęcia\Supermoce" required maxLength={4096} disabled={busy} aria-describedby="superpower-import-limits" />
      <p id="superpower-import-limits" className="field-hint">{t('superpowerPhotos.folderHint')}</p>
      <label><input type="checkbox" checked={dryRun} disabled={busy} onChange={event => setDryRun(event.target.checked)} /> {t('superpowerPhotos.dryRun')}</label>
      <button className="primary" disabled={busy || !folder.trim()}>{dryRun ? t('superpowerPhotos.submitPreview') : t('superpowerPhotos.submitImport')}</button>
    </form>
    {busy && <p role="status">{t('superpowerPhotos.busy')}</p>}
    {error && <p className="field-error" role="alert">{error}</p>}
    {report && <div aria-live="polite">
      <p>{report.dryRun ? '' : `${t('superpowerPhotos.count.imported')}: ${report.imported} · `}{t('superpowerPhotos.count.wouldImport')}: {report.wouldImport} · {t('superpowerPhotos.count.skipped')}: {report.skipped} · {t('superpowerPhotos.count.failed')}: {report.failed}</p>
      {!report.items.length && <p>{t('superpowerPhotos.emptyFolder')}</p>}
      <ul className="import-results">{report.items.map(item => <li key={item.filename}><strong>{item.filename}{item.superpowerName ? ` · ${item.superpowerName}` : ''}</strong><span>{t(statusKeys[item.status])}: {item.message}</span></li>)}</ul>
    </div>}
  </section>
}
