import { useState } from 'react'
import { api } from './api.js'
import ParticipationBackfillPanel from './ParticipationBackfillPanel.jsx'
import AdminUserForm from './AdminUserForm.jsx'
import AdminNewsForm from './AdminNewsForm.jsx'

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
  return <div className="admin-tools"><AdminNewsForm /><AdminUserForm /><ParticipationBackfillPanel onChanged={onImported} /><section className="card admin-photo-import" aria-labelledby="clique-import-title">
    <h3 id="clique-import-title">Import zdjęć klik</h3>
    <p>Wskaż folder na komputerze, na którym działa backend. Nazwy plików muszą odpowiadać ID istniejących klik, np. 12.jpg lub 27.png.</p>
    <p>Zdjęcia zostaną skopiowane jako pliki JPEG z nazwami UUID i przypisane do klik. Oryginały pozostaną w folderze. Kliki z przypisanym zdjęciem zostaną pominięte.</p>
    <form onSubmit={submit}>
      <label htmlFor="clique-photo-folder">Ścieżka do folderu</label>
      <input id="clique-photo-folder" value={folder} onChange={event => setFolder(event.target.value)} placeholder="C:\Zdjęcia\Kliki" required maxLength={4096} disabled={busy} aria-describedby="clique-import-limits" />
      <p id="clique-import-limits" className="field-hint">JPG, PNG lub GIF · do 4 MB i 16 megapikseli na zdjęcie · bez podfolderów. Przy kilku zdjęciach tego samego ID usuń duplikaty przed importem.</p>
      <button className="primary" disabled={busy || !folder.trim()}>{busy ? 'Importowanie…' : 'Importuj zdjęcia'}</button>
    </form>
    {busy && <p role="status">Trwa kopiowanie i przypisywanie zdjęć…</p>}
    {error && <p className="field-error" role="alert">{error}</p>}
    {report && <div aria-live="polite"><p>Zaimportowano: {report.imported} · Pominięto: {report.skipped} · Błędy: {report.failed}</p>
      {!report.items.length && <p>Folder jest pusty.</p>}
      <ul className="import-results">{report.items.map(item => <li key={item.filename}><strong>{item.filename}</strong><span>{item.message}</span></li>)}</ul>
    </div>}
  </section></div>
}
