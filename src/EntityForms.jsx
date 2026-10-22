import { useState } from 'react'

export function CliqueForm({ places, onSave }) {
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  async function submit(event) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const cliqueName = String(data.get('cliqueName')).trim()
    if (cliqueName.length < 2 || cliqueName.length > 32) {
      setError('Nazwa kliki powinna mieć od 2 do 32 znaków.')
      return
    }
    setSaving(true)
    try { await onSave({ cliqueName, cliqueCapital: places.find(place => String(place.id) === data.get('cliqueCapital')) || null }) }
    catch (error) { setError(error.message) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}>
    <p className="form-intro">Daj swojej ekipie wspólną nazwę.</p>
    <label htmlFor="clique-name">Nazwa kliki</label>
    <input autoFocus id="clique-name" name="cliqueName" required minLength={2} maxLength={32} onChange={() => setError('')} aria-describedby="clique-name-help" aria-invalid={!!error} />
    <small id="clique-name-help" className="field-hint">Od 2 do 32 znaków.</small>
    {error && <p className="field-error" role="alert">{error}</p>}
    <label htmlFor="clique-capital">Stolica kliki (opcjonalnie)</label>
    <select id="clique-capital" name="cliqueCapital"><option value="">Bez stolicy</option>{places.map(place => <option key={place.id} value={place.id}>{place.name}</option>)}</select>
    {!places.length && <small className="field-hint">Dodaj najpierw miejsce, aby wybrać je jako stolicę kliki.</small>}
    <p className="demo-note">Członków przypiszesz, wybierając klikę w formularzu osoby. Klika zostanie zapisana w bazie developerskiej.</p>
    <button className="primary submit" disabled={saving}>{saving ? 'Zapisywanie…' : 'Dodaj klikę →'}</button>
  </form>
}

export function PlaceForm({ onSave }) {
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  async function submit(event) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const latitude = Number(data.get('latitude'))
    const longitude = Number(data.get('longitude'))
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return
    setSaving(true)
    setError('')
    try { await onSave({ name: String(data.get('name')).trim(), latitude, longitude, category: String(data.get('category')).trim() }) }
    catch (error) { setError(error.message) } finally { setSaving(false) }
  }
  return <form onSubmit={submit}>
    <p className="form-intro">Zapisz miejsce, do którego prowadzą Twoje historie.</p>
    <label htmlFor="place-name">Nazwa miejsca</label><input autoFocus id="place-name" name="name" required pattern=".*\S.*" />
    <label htmlFor="place-category">Kategoria (opcjonalnie)</label><input id="place-category" name="category" placeholder="Np. kawiarnia, dom, park" />
    <div className="form-grid">
      <div><label htmlFor="place-latitude">Szerokość geograficzna</label><input id="place-latitude" name="latitude" type="number" min={-90} max={90} step="any" required placeholder="52.2297" aria-describedby="latitude-help" /><small id="latitude-help" className="field-hint">Od −90 do 90.</small></div>
      <div><label htmlFor="place-longitude">Długość geograficzna</label><input id="place-longitude" name="longitude" type="number" min={-180} max={180} step="any" required placeholder="21.0122" aria-describedby="longitude-help" /><small id="longitude-help" className="field-hint">Od −180 do 180.</small></div>
    </div>
    <p className="demo-note">Miejsce będzie dostępne jako dom osoby i stolica kliki. Zapis w bazie developerskiej.</p>
    {error && <p className="field-error" role="alert">{error}</p>}
    <button className="primary submit" disabled={saving}>{saving ? 'Zapisywanie…' : 'Dodaj miejsce →'}</button>
  </form>
}
