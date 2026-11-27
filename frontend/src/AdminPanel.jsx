import { useState } from 'react'
import { api } from './api.js'
import ParticipationBackfillPanel from './ParticipationBackfillPanel.jsx'
import AdminUserForm from './AdminUserForm.jsx'
import AdminNewsForm from './AdminNewsForm.jsx'
import SuperpowerPhotoImportPanel from './SuperpowerPhotoImportPanel.jsx'
import { t } from './i18n.js'

export default function AdminPanel({ onImported }) {
  const [folder, setFolder] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [report, setReport] = useState(null)
  async function submit(event) {
    event.preventDefault()
    setBusy(true); setError(''); setReport(null)
    try {
      const result = await api('/admin/cliques/photos/import', { body: { folder: folder.trim() } })
      setReport(result)
      if (result.imported) onImported()
    } catch (error) { setError(error.message) } finally { setBusy(false) }
  }
  return <div className="admin-tools"><AdminNewsForm /><AdminUserForm /><ParticipationBackfillPanel onChanged={onImported} /><SuperpowerPhotoImportPanel /><section className="card admin-photo-import" aria-labelledby="clique-import-title">
    <h3 id="clique-import-title">{t('Import zdjęć klik')}</h3>
    <p>{t('Wskaż folder na komputerze, na którym działa backend. Nazwy plików muszą odpowiadać ID istniejących klik, np. 12.jpg lub 27.png.')}</p>
    <p>{t('Zdjęcia zostaną skopiowane jako pliki JPEG z nazwami UUID i przypisane do klik. Oryginały pozostaną w folderze. Kliki z przypisanym zdjęciem zostaną pominięte.')}</p>
    <form onSubmit={submit}>
      <label htmlFor="clique-photo-folder">{t('Ścieżka do folderu')}</label>
      <input id="clique-photo-folder" value={folder} onChange={event => setFolder(event.target.value)} placeholder="C:\Zdjęcia\Kliki" required maxLength={4096} disabled={busy} aria-describedby="clique-import-limits" />
      <p id="clique-import-limits" className="field-hint">{t('JPG, PNG lub GIF · do 4 MB i 16 megapikseli na zdjęcie · bez podfolderów. Przy kilku zdjęciach tego samego ID usuń duplikaty przed importem.')}</p>
      <button className="primary" disabled={busy || !folder.trim()}>{busy ? t('Importowanie…') : t('Importuj zdjęcia')}</button>
    </form>
    {busy && <p role="status">{t('Trwa kopiowanie i przypisywanie zdjęć…')}</p>}
    {error && <p className="field-error" role="alert">{error}</p>}
    {report && <div aria-live="polite"><p>{t('Zaimportowano')}: {report.imported} · {t('Pominięto')}: {report.skipped} · {t('Błędy')}: {report.failed}</p>
      {!report.items.length && <p>{t('Folder jest pusty.')}</p>}
      <ul className="import-results">{report.items.map(item => <li key={item.filename}><strong>{item.filename}</strong><span>{item.message}</span></li>)}</ul>
    </div>}
  </section></div>
}
