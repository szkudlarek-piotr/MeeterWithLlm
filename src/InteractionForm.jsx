import { useState } from 'react'
import EntityPicker from './EntityPicker.jsx'
import EventPhotoPicker from './EventPhotoPicker.jsx'

export const interactionTypes = {
  Spotkania: { type: 'MEETING', endpoint: '/meetings', icon: '☕', label: 'Dodaj spotkanie' },
  Wizyty: { type: 'VISIT', endpoint: '/visits', icon: '⌂', label: 'Dodaj wizytę' },
  Eventy: { type: 'EVENT', endpoint: '/events', icon: '✦', label: 'Dodaj event' },
  Podróże: { type: 'TRIP', endpoint: '/trips', icon: '↗', label: 'Dodaj podróż' },
  Współlokatorzy: { type: 'FLATMATE', endpoint: '/flatmates', icon: '⌂', label: 'Dodaj wspólne mieszkanie' },
  Śluby: { type: 'WEDDING', endpoint: '/weddings', icon: '♡', label: 'Dodaj ślub' },
}

export function interactionEntry(item) {
  const [category, config] = Object.entries(interactionTypes).find(([, value]) => value.type === item.type)
  const locations = [item.place?.name, item.details?.placeString, ...(item.details?.places || []).map(place => place.name), item.details?.ceremonyPlace?.name, item.details?.partyPlace?.name].filter(Boolean)
  return {
    id: `interaction-${item.id}`, interactionId: item.id, category, icon: config.icon, name: item.name, note: item.longDesc,
    canAddParticipants: item.canAddParticipants, removableParticipantIds: item.removableParticipantIds || [],
    date: item.startDate.slice(0, 10), endDate: item.endDate?.slice(0, 10), ongoing: item.type === 'FLATMATE' && !item.endDate,
    participants: item.participants, locations: [...new Set(locations)], details: item.details, type: item.type,
    startTime: ['MEETING', 'FLATMATE'].includes(item.type) ? null : item.startDate.slice(11, 16),
    endTime: ['MEETING', 'FLATMATE'].includes(item.type) ? null : item.endDate?.slice(11, 16),
  }
}

function localToday() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

export default function InteractionForm({ category, initialDate, actorId, onSave }) {
  const config = interactionTypes[category]
  const type = config.type
  const day = initialDate || localToday()
  const [start, setStart] = useState(['MEETING', 'FLATMATE'].includes(type) ? day : `${day}T18:00`)
  const [end, setEnd] = useState(type === 'FLATMATE' ? '' : `${day}T20:00`)
  const [people, setPeople] = useState([])
  const [place, setPlace] = useState([])
  const [route, setRoute] = useState([])
  const [roommate, setRoommate] = useState([])
  const [groom, setGroom] = useState([])
  const [bride, setBride] = useState([])
  const [ceremony, setCeremony] = useState([])
  const [party, setParty] = useState([])
  const [hotel, setHotel] = useState([])
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [coming, setComing] = useState('')
  const [leaving, setLeaving] = useState('')
  const [photo, setPhoto] = useState({})
  const [photoMode, setPhotoMode] = useState('none')
  const [readingPhoto, setReadingPhoto] = useState(false)
  const idOf = selection => selection[0]?.id ?? null

  async function submit(event) {
    event.preventDefault()
    if (saving || readingPhoto) return
    const data = new FormData(event.currentTarget)
    setError('')
    const details = { name: String(data.get('name')).trim(), longDesc: String(data.get('longDesc')).trim() || null, participantIds: people.map(person => person.id) }
    if (details.name.length < 2) { setError('Nazwa powinna mieć od 2 do 128 znaków.'); return }
    if (type === 'FLATMATE' && !roommate.length) { setError('Wybierz współlokatora z listy.'); return }
    if (type === 'WEDDING' && (!groom.length || !bride.length)) { setError('Wybierz obie osoby z listy.'); return }
    const payload = { details }
    if (type === 'MEETING') Object.assign(payload, { date: start, placeId: idOf(place), placeString: String(data.get('placeString')).trim() || null })
    if (type === 'VISIT') Object.assign(payload, { visitDate: start, duration: Number(data.get('duration')), placeId: idOf(place) })
    if (type === 'EVENT') {
      if (end < start || (coming && (coming < start || coming > end)) || (leaving && (leaving < (coming || start) || leaving > end))) { setError('Sprawdź kolejność dat i godzin. Twój udział musi mieścić się w czasie wydarzenia.'); return }
      if (photoMode !== 'none' && !photo.photoUrl && !photo.photoData && !photo.photoEventId) { setError('Wybierz zdjęcie lub opcję „Bez zdjęcia”.'); return }
      Object.assign(payload, { startDate: start, stopDate: end, meComingDate: coming || null, meLeavingDate: leaving || null, placeId: idOf(place), placeString: String(data.get('placeString') || '').trim() || null, ...photo })
    }
    if (type === 'TRIP') Object.assign(payload, { startDate: start, endDate: end, placeIds: route.map(item => item.id) })
    if (type === 'FLATMATE') Object.assign(payload, { humanId: idOf(roommate), startDate: start, stopDate: end || null, placeId: idOf(place) })
    if (type === 'WEDDING') Object.assign(payload, { dateTime: start, groomId: idOf(groom), brideId: idOf(bride), ceremonyPlaceId: idOf(ceremony), partyPlaceId: idOf(party), hotelId: idOf(hotel), hadAfter: data.get('hadAfter') === 'on', wasIInvited: data.get('wasIInvited') === 'on' })
    setSaving(true)
    try { await onSave(config.endpoint, payload) }
    catch (error) { setError(error.message) }
    finally { setSaving(false) }
  }

  const dateType = ['MEETING', 'FLATMATE'].includes(type) ? 'date' : 'datetime-local'
  return <form onSubmit={submit} className="interaction-form">
    <p className="form-intro">Zapisz wspólną chwilę. Dane zostaną zachowane w Twoim Meeterze.</p>
    <fieldset disabled={saving}>
      <label htmlFor="interaction-name">{type === 'EVENT' ? 'Nazwa wydarzenia' : 'Nazwa'} *</label><input autoFocus id="interaction-name" name="name" required minLength={2} maxLength={128} placeholder={type === 'EVENT' ? 'Np. koncert, lekcja tańca, festiwal' : type === 'FLATMATE' ? 'Np. Wspólne mieszkanie na Mokotowie' : 'Jak zapamiętasz tę chwilę?'} />
      <div className="form-grid">
        <div><label htmlFor="interaction-start">{type === 'MEETING' ? 'Data spotkania' : type === 'WEDDING' ? 'Data i godzina ślubu' : 'Początek'} *</label><input id="interaction-start" type={dateType} required value={start} onChange={event => { const next = event.target.value; setStart(next); if (type === 'EVENT' && next > end) setEnd(next) }} max={dateType === 'date' ? '9999-12-31' : '9999-12-31T23:59'} /></div>
        {['EVENT', 'TRIP', 'FLATMATE'].includes(type) && <div><label htmlFor="interaction-end">Koniec {type === 'FLATMATE' ? '(opcjonalnie)' : '*'}</label><input id="interaction-end" type={dateType} value={end} onChange={event => setEnd(event.target.value)} min={start} max={dateType === 'date' ? '9999-12-31' : '9999-12-31T23:59'} required={type !== 'FLATMATE'} /></div>}
        {type === 'VISIT' && <div><label htmlFor="visit-duration">Czas trwania w dniach *</label><input id="visit-duration" name="duration" type="number" min={1} max={3650} step={1} defaultValue={1} required /><small className="field-hint">Łącznie z pierwszym dniem, np. piątek–niedziela = 3.</small></div>}
      </div>
      {type === 'FLATMATE' && <><small className="field-hint">Pusty koniec oznacza, że nadal mieszkacie razem.</small><EntityPicker label="Współlokator" source="people" value={roommate} onChange={setRoommate} required excludeIds={[actorId]} /></>}
      {type === 'EVENT' && <><div className="form-grid"><div><label htmlFor="event-coming">Moje przybycie (opcjonalnie)</label><input id="event-coming" type="datetime-local" min={start} max={end} value={coming} onChange={event => { const next = event.target.value; setComing(next); if (leaving && next > leaving) setLeaving(next) }} /></div><div><label htmlFor="event-leaving">Moje wyjście (opcjonalnie)</label><input id="event-leaving" type="datetime-local" min={coming || start} max={end} value={leaving} onChange={event => setLeaving(event.target.value)} /></div></div><p className="field-hint">Puste przybycie lub wyjście oznacza odpowiednio początek lub koniec wydarzenia.</p></>}
      {type === 'WEDDING' && <><div className="form-grid"><EntityPicker label="Pan młody" source="people" value={groom} onChange={setGroom} required excludeIds={bride.map(person => person.id)} /><EntityPicker label="Panna młoda" source="people" value={bride} onChange={setBride} required excludeIds={groom.map(person => person.id)} /></div><EntityPicker label="Miejsce ceremonii (opcjonalnie)" source="places" value={ceremony} onChange={setCeremony} /><EntityPicker label="Miejsce wesela (opcjonalnie)" source="places" value={party} onChange={setParty} /><EntityPicker label="Hotel (opcjonalnie)" source="places" value={hotel} onChange={setHotel} /><label className="checkbox-label"><input name="wasIInvited" type="checkbox" defaultChecked /> Byłem / byłam zaproszony(-a) na wesele</label><label className="checkbox-label"><input name="hadAfter" type="checkbox" /> Były / są zaplanowane poprawiny</label></>}
      {['MEETING', 'VISIT', 'EVENT', 'FLATMATE'].includes(type) && <EntityPicker label={type === 'FLATMATE' ? 'Wspólny dom (opcjonalnie)' : 'Miejsce (opcjonalnie)'} source="places" value={place} onChange={setPlace} />}
      {['MEETING', 'EVENT'].includes(type) && <><label htmlFor="meeting-place-string">Dodatkowy opis miejsca (opcjonalnie)</label><input id="meeting-place-string" name="placeString" maxLength={200} placeholder="Np. stolik przy oknie lub miejsce spoza listy" /></>}
      {type === 'TRIP' && <EntityPicker label="Odwiedzone miejsca (opcjonalnie)" source="places" value={route} onChange={setRoute} multiple />}
      {type !== 'FLATMATE' && <EntityPicker label={type === 'EVENT' ? 'Spotkane osoby (opcjonalnie)' : type === 'WEDDING' ? 'Pozostali goście (opcjonalnie)' : 'Uczestnicy (opcjonalnie)'} source="people" value={people} onChange={setPeople} multiple excludeIds={[actorId, ...groom.map(item => item.id), ...bride.map(item => item.id)]} />}
      {type === 'EVENT' && <p className="field-hint">Wybierz znajomych, których spotkałeś na wydarzeniu. To nie jest pełna lista jego uczestników.</p>}
      <p className="field-hint participant-help">Ty jesteś dodawany automatycznie. {type === 'WEDDING' ? 'Para młoda również trafia do uczestników.' : ''}</p>
      <label htmlFor="interaction-description">Opis / wspomnienie (opcjonalnie)</label><textarea id="interaction-description" name="longDesc" rows={4} maxLength={10240} placeholder="Co chcesz zapamiętać?" />
      {type === 'EVENT' && <EventPhotoPicker value={photo} onChange={setPhoto} onBusy={setReadingPhoto} onModeChange={setPhotoMode} />}
      {error && <p className="field-error" role="alert">{error}</p>}
      <button className="primary submit" type="submit" disabled={readingPhoto}>{readingPhoto ? 'Odczytywanie zdjęcia…' : saving ? 'Zapisywanie…' : config.label + ' →'}</button>
    </fieldset>
  </form>
}
