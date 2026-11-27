import { PhotoPicker, PhotoPreview } from './styles/EventPhotoPicker.js'
import { useEffect, useId, useRef, useState } from 'react'
import { api } from './api.js'
import { t } from './i18n.js'

export default function EventPhotoPicker({ value, onChange, onBusy, onModeChange, currentPhoto }) {
  const pickerId = useId()
  const fieldId = name => pickerId + '-' + name
  const [mode, setMode] = useState(currentPhoto ? 'current' : 'none')
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [results, setResults] = useState([])
  const [hasNext, setHasNext] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [preview, setPreview] = useState(currentPhoto?.url || '')
  const reader = useRef(null)
  useEffect(() => () => reader.current?.abort(), [])
  useEffect(() => {
    if (mode !== 'database') return
    const controller = new AbortController()
    setLoading(true); setError('')
    const timer = setTimeout(() => {
      api(`/interactions?type=EVENT&size=12&page=${page}&q=${encodeURIComponent(query)}`, { signal: controller.signal })
        .then(data => { setResults(data.content.filter(item => item.details.photoUrl)); setHasNext(data.hasNext) })
        .catch(error => { if (error.name !== 'AbortError') setError(error.message) })
        .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    }, 250)
    return () => { clearTimeout(timer); controller.abort() }
  }, [mode, query, page])

  function changeMode(next) {
    reader.current?.abort(); reader.current = null
    onBusy(false); setMode(next); onModeChange(next); setError(''); setPreview(''); onChange({})
    if (next === 'current') {
      setPreview(currentPhoto.url)
      onChange({ photoEventId: currentPhoto.id, photoName: currentPhoto.name || '' })
    }
  }
  function chooseFile(file) {
    reader.current?.abort(); onChange({ photoName: value.photoName }); setPreview(''); setError(''); onBusy(false)
    if (!file) return
    if (file.size > 4 * 1024 * 1024) { setError(t('Zdjęcie może mieć maksymalnie 4 MB.')); return }
    const next = new FileReader(); reader.current = next; onBusy(true)
    next.onload = () => {
      setPreview(String(next.result)); onChange({ photoName: value.photoName, photoData: String(next.result).split(',')[1] }); onBusy(false)
    }
    next.onerror = () => { setError(t('Nie udało się odczytać pliku.')); onBusy(false) }
    next.readAsDataURL(file)
  }
  return <PhotoPicker aria-label={t('Zdjęcie wydarzenia')}>
    <label htmlFor={fieldId('event-photo-mode')}>{t('Zdjęcie wydarzenia (opcjonalnie)')}</label>
    <select id={fieldId('event-photo-mode')} value={mode} onChange={event => changeMode(event.target.value)}>
      {currentPhoto && <option value="current">{t('Zachowaj obecne zdjęcie')}</option>}
      <option value="none">{t('Bez zdjęcia')}</option><option value="database">{t('Z wcześniejszego wydarzenia')}</option><option value="file">{t('Z pliku')}</option><option value="link">{t('Z linku')}</option>
    </select>
    {mode !== 'none' && <><label htmlFor={fieldId('event-photo-name')}>{t('Nazwa zdjęcia (opcjonalnie)')}</label><input id={fieldId('event-photo-name')} maxLength={255} value={value.photoName || ''} onChange={event => onChange({ ...value, photoName: event.target.value })} placeholder={t('Np. scena główna')} /></>}
    {mode === 'link' && <><label htmlFor={fieldId('event-photo-url')}>{t('Publiczny link do zdjęcia')} *</label><input id={fieldId('event-photo-url')} type="url" required maxLength={2048} value={value.photoUrl || ''} onChange={event => onChange({ ...value, photoUrl: event.target.value })} placeholder="https://…" /><p className="field-hint">{t('Bezpośredni link do JPG, PNG lub GIF. Zdjęcie zostanie pobrane przy zapisie wydarzenia.')}</p></>}
    {mode === 'file' && <><label htmlFor={fieldId('event-photo-file')}>{t('Plik zdjęcia')} *</label><input id={fieldId('event-photo-file')} type="file" accept="image/jpeg,image/png,image/gif" required onChange={event => chooseFile(event.target.files[0])} /></>}
    {mode === 'database' && <><label htmlFor={fieldId('event-photo-search')}>{t('Wyszukaj wcześniejsze wydarzenie')}</label><input id={fieldId('event-photo-search')} value={query} maxLength={200} onChange={event => { setQuery(event.target.value); setPage(0) }} />
      <div className="event-photo-options" aria-busy={loading}>{!loading && results.map(item => <button type="button" key={item.id} aria-pressed={value.photoEventId === item.id} onClick={() => { onChange({ photoName: value.photoName || item.details.photoName || '', photoEventId: item.id }); setPreview(item.details.photoUrl) }}><img src={item.details.photoUrl} alt="" /><span>{item.name}</span></button>)}</div>
      {loading ? <p role="status">{t('Wczytywanie zdjęć…')}</p> : !results.length && <p className="field-hint">{t('Na tej stronie nie ma wydarzeń ze zdjęciem.')}</p>}
      <div className="event-photo-pages"><button type="button" disabled={!page || loading} onClick={() => setPage(page - 1)}>{t('Poprzednia strona')}</button><span>{page + 1}</span><button type="button" disabled={!hasNext || loading} onClick={() => setPage(page + 1)}>{t('Następna strona')}</button></div>
      {!value.photoEventId && <p className="field-hint">{t('Wybierz zdjęcie albo opcję „Bez zdjęcia”.')}</p>}
    </>}
    {preview && <PhotoPreview src={preview} alt={t('Wybrane zdjęcie wydarzenia')} />}
    {mode !== 'none' && <small className="field-hint">{t('Maksymalnie 4 MB i 16 megapikseli. Zdjęcie zachowa proporcje.')}</small>}
    {error && <p className="field-error" role="alert">{error}</p>}
  </PhotoPicker>
}
