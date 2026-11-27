import { useEffect, useState } from 'react'
import { api } from './api.js'
import { t } from './i18n.js'

export default function HumanForm({ onSave, cliques = [], places = [] }) {
  const [cliqueQuery, setCliqueQuery] = useState('')
  const [clique, setClique] = useState(null)
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [matches, setMatches] = useState(cliques)
  const [loading, setLoading] = useState(false)
  const [searchError, setSearchError] = useState('')
  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    setMatches([])
    setActive(-1)
    setSearchError('')
    api(`/cliques?q=${encodeURIComponent(cliqueQuery)}&limit=100`, { signal: controller.signal })
      .then(setMatches)
      .catch(error => { if (!controller.signal.aborted) setSearchError(error.message) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [cliqueQuery])

  function select(item) {
    setClique(item)
    setCliqueQuery(item.cliqueName)
    setOpen(false)
    setActive(-1)
    setError('')
  }

  async function submit(event) {
    event.preventDefault()
    if (cliqueQuery.trim() && !clique) {
      setError(t('Wybierz klikę z listy albo wyczyść pole.'))
      return
    }
    const data = new FormData(event.currentTarget)
    setSaving(true)
    try { await onSave({
      name: String(data.get('name')).trim(),
      surname: String(data.get('surname')).trim(),
      birthdayDate: data.get('birthdayDate') || null,
      fbLink: String(data.get('fbLink')).trim() || null,
      gender: data.get('gender') || null,
      livesIn: String(data.get('livesIn')).trim() || null,
      clique,
      home: places.find(place => String(place.id) === data.get('home')) || null,
      song: null,
      superpowers: [],
      profilePhotoUrl: String(data.get('profilePhotoUrl')).trim() || null,
    }) } catch (error) { setError(error.message) } finally { setSaving(false) }
  }

  return <form onSubmit={submit} className="human-form">
    <p className="form-intro">{t('Poznajmy kogoś z Twojego świata.')}</p>
    <div className="form-grid">
      <div><label htmlFor="human-name">{t('Imię')}</label><input autoFocus id="human-name" name="name" required maxLength={100} pattern=".*\S.*" /></div>
      <div><label htmlFor="human-surname">{t('Nazwisko')}</label><input id="human-surname" name="surname" maxLength={100} /></div>
      <div><label htmlFor="human-birthday">{t('Data urodzenia')}</label><input id="human-birthday" name="birthdayDate" type="date" /></div>
      <div><label htmlFor="human-gender">{t('Płeć')}</label><input id="human-gender" name="gender" maxLength={1} placeholder={t('Kod płci (1 znak)')} /><small className="field-hint">{t('Opcjonalny kod zgodny z Twoim Meeterem.')}</small></div>
      <div className="full-width"><label htmlFor="human-city">{t('Mieszka w')}</label><input id="human-city" name="livesIn" placeholder={t('Np. Warszawa')} maxLength={200} /></div>
      <div className="full-width"><label htmlFor="human-home">{t('Dom / miejsce zamieszkania')}</label><select id="human-home" name="home"><option value="">{t('Bez przypisanego miejsca')}</option>{places.map(place => <option key={place.id} value={place.id}>{place.name}</option>)}</select>{!places.length && <small className="field-hint">{t('Najpierw dodaj miejsce w menu po lewej.')}</small>}</div>
      <div className="full-width"><label htmlFor="human-facebook">{t('Link do Facebooka')}</label><input id="human-facebook" name="fbLink" type="url" placeholder="https://www.facebook.com/…" /></div>
      <div className="full-width"><label htmlFor="human-photo">{t('Link do zdjęcia profilowego (opcjonalnie)')}</label><input id="human-photo" name="profilePhotoUrl" type="url" maxLength={2048} placeholder="https://przyklad.pl/zdjecie.jpg" /><small className="field-hint">{t('Bezpośredni link do JPG, PNG lub GIF, do 4 MB. Meeter pobierze zdjęcie i zapisze własną kopię.')}</small></div>
      <div className="full-width clique-field" onBlur={event => {
        if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false)
      }}>
        <label htmlFor="human-clique">{t('Klika')}</label>
        <div className="clique-input"><input id="human-clique" role="combobox" aria-autocomplete="list" aria-expanded={open} aria-controls="clique-options" aria-activedescendant={open && active >= 0 ? `clique-${matches[active]?.id}` : undefined} aria-describedby="clique-help" aria-invalid={!!error} autoComplete="off" value={cliqueQuery} placeholder={t('Zacznij pisać nazwę kliki…')} onFocus={() => setOpen(true)} onChange={event => { setCliqueQuery(event.target.value); setClique(null); setOpen(true); setActive(-1); setError('') }} onKeyDown={event => {
          if (event.key === 'ArrowDown') { event.preventDefault(); setOpen(true); setActive(index => Math.min(index + 1, matches.length - 1)) }
          if (event.key === 'ArrowUp') { event.preventDefault(); setActive(index => Math.max(0, index - 1)) }
          if (event.key === 'Enter' && open && active >= 0 && matches[active]) { event.preventDefault(); select(matches[active]) }
          if (event.key === 'Escape' && open) { event.preventDefault(); event.stopPropagation(); setOpen(false) }
        }} />{cliqueQuery && <button type="button" aria-label={t('Wyczyść klikę')} onClick={() => { setCliqueQuery(''); setClique(null); setError(''); setOpen(false) }}>×</button>}</div>
        {open && <div id="clique-options" role="listbox" aria-label={t('Dostępne kliki')} aria-busy={loading} className="clique-options">{matches.length ? matches.map((item, index) => <div key={item.id} id={`clique-${item.id}`} role="option" aria-selected={clique?.id === item.id} className={active === index ? 'highlighted' : ''} onMouseDown={event => event.preventDefault()} onClick={() => select(item)}>{item.cliqueName}{clique?.id === item.id && <span>✓</span>}</div>) : <p role="status">{loading ? t('Wyszukiwanie…') : searchError || t('Brak pasujących klik.')}</p>}</div>}
        <small id="clique-help" className="field-hint">{t('Lista filtruje się po każdej literze. Wybierz istniejącą klikę; możesz użyć strzałek i Enter.')}</small>
        {error && <p className="field-error" role="alert">{error}</p>}
      </div>
    </div>
    <p className="relation-note">{t('Zdjęcie jest pobierane podczas zapisu. Piosenkę i przypisywanie supermocy dodamy później.')}</p>
    <p className="demo-note">{t('Osoba zostanie zapisana w osobnej bazie developerskiej.')}</p>
    <button className="primary submit" type="submit" disabled={saving}>{saving ? t('Zapisywanie…') : t('Dodaj osobę →')}</button>
  </form>
}
