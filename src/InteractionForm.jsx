import { useId, useState } from 'react'
import EntityPicker from './EntityPicker.jsx'
import EventPhotoPicker from './EventPhotoPicker.jsx'
import ParticipantRecommendations from './ParticipantRecommendations.jsx'

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
    recommendationPlaces: [...new Map([item.place, ...(item.details?.places || []), item.details?.ceremonyPlace, item.details?.partyPlace, item.details?.hotel].filter(Boolean).map(place => [place.id, place])).values()],
    startTime: ['MEETING', 'FLATMATE'].includes(item.type) ? null : item.startDate.slice(11, 16),
    endTime: ['MEETING', 'FLATMATE'].includes(item.type) ? null : item.endDate?.slice(11, 16),
  }
}

function localToday() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

export default function InteractionForm({ category, initialDate, actorId, onSave, initialInteraction }) {
  const formId = useId()
  const fieldId = name => formId + '-' + name
  const config = interactionTypes[category]
  const type = config.type
  const day = initialDate || localToday()
  const editing = Boolean(initialInteraction)
  const initial = initialInteraction || {}
  const extra = initial.details || {}
  const selection = item => item ? [item] : []
  const initialPhoto = extra.photoUrl ? { photoEventId: initial.id, photoName: extra.photoName || '' } : {}
  const [start, setStart] = useState(initial.startDate ? (['MEETING', 'FLATMATE'].includes(type) ? initial.startDate.slice(0, 10) : initial.startDate) : ['MEETING', 'FLATMATE'].includes(type) ? day : `${day}T18:00`)
  const [end, setEnd] = useState(initial.endDate ? (type === 'FLATMATE' ? initial.endDate.slice(0, 10) : initial.endDate) : type === 'FLATMATE' ? '' : `${day}T20:00`)
  const [people, setPeople] = useState([])
  const [name, setName] = useState(initial.name || '')
  const [placeText, setPlaceText] = useState(extra.placeString || '')
  const [place, setPlace] = useState(selection(initial.place))
  const [route, setRoute] = useState(extra.places || [])
  const [roommate, setRoommate] = useState(selection(extra.human))
  const [groom, setGroom] = useState(selection(extra.groom))
  const [bride, setBride] = useState(selection(extra.bride))
  const [ceremony, setCeremony] = useState(selection(extra.ceremonyPlace))
  const [party, setParty] = useState(selection(extra.partyPlace))
  const [hotel, setHotel] = useState(selection(extra.hotel))
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [coming, setComing] = useState(extra.meComingDate || '')
  const [leaving, setLeaving] = useState(extra.meLeavingDate || '')
  const [photo, setPhoto] = useState(initialPhoto)
  const [photoMode, setPhotoMode] = useState(extra.photoUrl ? 'current' : 'none')
  const [readingPhoto, setReadingPhoto] = useState(false)
  const idOf = selection => selection[0]?.id ?? null

  async function submit(event) {
    event.preventDefault()
    if (saving || readingPhoto) return
    const data = new FormData(event.currentTarget)
    setError('')
    const details = { name: String(data.get('name')).trim(), longDesc: String(data.get('longDesc')).trim() || null, participantIds: editing ? [] : people.map(person => person.id) }
    if (details.name.length < 2) { setError('Nazwa powinna mieć od 2 do 128 znaków.'); return }
    if (type === 'FLATMATE' && !roommate.length) { setError('Wybierz współlokatora z listy.'); return }
    if (type === 'WEDDING' && (!groom.length || !bride.length)) { setError('Wybierz obie osoby z listy.'); return }
    const payload = { details }
    if (type === 'MEETING') Object.assign(payload, { date: start, placeId: idOf(place), placeString: String(data.get('placeString')).trim() || null })
    if (type === 'VISIT') Object.assign(payload, { visitDate: start, duration: Number(data.get('duration')), placeId: idOf(place) })
    if (type === 'EVENT') {
      if (new Date(end) < new Date(start) || (coming && (new Date(coming) < new Date(start) || new Date(coming) > new Date(end))) || (leaving && (new Date(leaving) < new Date(coming || start) || new Date(leaving) > new Date(end)))) { setError('Sprawdź kolejność dat i godzin. Twój udział musi mieścić się w czasie wydarzenia.'); return }
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
    <p className="form-intro">{editing ? 'Zmień dane zapisanej interakcji.' : 'Zapisz wspólną chwilę. Dane zostaną zachowane w Twoim Meeterze.'}</p>
    <fieldset disabled={saving}>
      <label htmlFor={fieldId('interaction-name')}>{type === 'EVENT' ? 'Nazwa wydarzenia' : 'Nazwa'} *</label><input autoFocus id={fieldId('interaction-name')} name="name" value={name} onChange={event => setName(event.target.value)} required minLength={2} maxLength={128} placeholder={type === 'EVENT' ? 'Np. koncert, lekcja tańca, festiwal' : type === 'FLATMATE' ? 'Np. Wspólne mieszkanie na Mokotowie' : 'Jak zapamiętasz tę chwilę?'} />
      <div className="form-grid">
        <div><label htmlFor={fieldId('interaction-start')}>{type === 'MEETING' ? 'Data spotkania' : type === 'WEDDING' ? 'Data i godzina ślubu' : 'Początek'} *</label><input id={fieldId('interaction-start')} type={dateType} step={1} required value={start} onChange={event => { const next = event.target.value; setStart(next); if (type === 'EVENT' && new Date(next) > new Date(end)) setEnd(next) }} max={dateType === 'date' ? '9999-12-31' : '9999-12-31T23:59'} /></div>
        {['EVENT', 'TRIP', 'FLATMATE'].includes(type) && <div><label htmlFor={fieldId('interaction-end')}>Koniec {type === 'FLATMATE' ? '(opcjonalnie)' : '*'}</label><input id={fieldId('interaction-end')} type={dateType} step={1} value={end} onChange={event => setEnd(event.target.value)} min={start} max={dateType === 'date' ? '9999-12-31' : '9999-12-31T23:59'} required={type !== 'FLATMATE'} /></div>}
        {type === 'VISIT' && <div><label htmlFor={fieldId('visit-duration')}>Czas trwania w dniach *</label><input id={fieldId('visit-duration')} name="duration" type="number" min={1} max={3650} step={1} defaultValue={extra.duration || 1} required /><small className="field-hint">Łącznie z pierwszym dniem, np. piątek–niedziela = 3.</small></div>}
      </div>
      {type === 'FLATMATE' && <><small className="field-hint">Pusty koniec oznacza, że nadal mieszkacie razem.</small><EntityPicker label="Współlokator" source="people" interactionType={type} value={roommate} onChange={setRoommate} required excludeIds={[actorId]} /></>}
      {type === 'EVENT' && <><div className="form-grid"><div><label htmlFor={fieldId('event-coming')}>Moje przybycie (opcjonalnie)</label><input id={fieldId('event-coming')} type="datetime-local" step={1} min={start} max={end} value={coming} onChange={event => { const next = event.target.value; setComing(next); if (leaving && new Date(next) > new Date(leaving)) setLeaving(next) }} /></div><div><label htmlFor={fieldId('event-leaving')}>Moje wyjście (opcjonalnie)</label><input id={fieldId('event-leaving')} type="datetime-local" step={1} min={coming || start} max={end} value={leaving} onChange={event => setLeaving(event.target.value)} /></div></div><p className="field-hint">Puste przybycie lub wyjście oznacza odpowiednio początek lub koniec wydarzenia.</p></>}
      {type === 'WEDDING' && <><div className="form-grid"><EntityPicker label="Pan młody" source="people" interactionType={type} value={groom} onChange={setGroom} required excludeIds={bride.map(person => person.id)} /><EntityPicker label="Panna młoda" source="people" interactionType={type} value={bride} onChange={setBride} required excludeIds={groom.map(person => person.id)} /></div><EntityPicker label="Miejsce ceremonii (opcjonalnie)" source="places" value={ceremony} onChange={setCeremony} /><EntityPicker label="Miejsce wesela (opcjonalnie)" source="places" value={party} onChange={setParty} /><EntityPicker label="Hotel (opcjonalnie)" source="places" value={hotel} onChange={setHotel} /><label className="checkbox-label"><input name="wasIInvited" type="checkbox" defaultChecked={editing ? Boolean(extra.wasIInvited) : true} /> Byłem / byłam zaproszony(-a) na wesele</label><label className="checkbox-label"><input name="hadAfter" type="checkbox" defaultChecked={Boolean(extra.hadAfter)} /> Były / są zaplanowane poprawiny</label></>}
      {['MEETING', 'VISIT', 'EVENT', 'FLATMATE'].includes(type) && <EntityPicker label={type === 'FLATMATE' ? 'Wspólny dom (opcjonalnie)' : 'Miejsce (opcjonalnie)'} source="places" value={place} onChange={setPlace} />}
      {['MEETING', 'EVENT'].includes(type) && <><label htmlFor={fieldId('meeting-place-string')}>Dodatkowy opis miejsca (opcjonalnie)</label><input id={fieldId('meeting-place-string')} name="placeString" value={placeText} onChange={event => setPlaceText(event.target.value)} maxLength={200} placeholder="Np. stolik przy oknie lub miejsce spoza listy" /></>}
      {type === 'TRIP' && <EntityPicker label="Odwiedzone miejsca (opcjonalnie)" source="places" value={route} onChange={setRoute} multiple />}
      {!editing && type !== 'FLATMATE' && <ParticipantRecommendations startDate={start} name={name} placeText={placeText} places={type === 'TRIP' ? route : type === 'WEDDING' ? [...ceremony, ...party, ...hotel] : place} guests={[...people, ...groom, ...bride]} onSelect={person => setPeople(current => current.some(item => item.id === person.id) ? current : [...current, person])} />}
      {!editing && type !== 'FLATMATE' && <EntityPicker label={type === 'EVENT' ? 'Spotkane osoby (opcjonalnie)' : type === 'WEDDING' ? 'Pozostali goście (opcjonalnie)' : 'Uczestnicy (opcjonalnie)'} source="people/participants" interactionType={type} value={people} onChange={setPeople} multiple excludeIds={[actorId, ...groom.map(item => item.id), ...bride.map(item => item.id)]} />}
      {type === 'EVENT' && <p className="field-hint">Wybierz znajomych, których spotkałeś na wydarzeniu. To nie jest pełna lista jego uczestników.</p>}
      <p className="field-hint participant-help">{editing ? 'Uczestników możesz zmienić w szczegółach interakcji.' : <>Ty jesteś dodawany automatycznie. {type === 'WEDDING' ? 'Para młoda również trafia do uczestników.' : ''}</>}</p>
      <label htmlFor={fieldId('interaction-description')}>Opis / wspomnienie (opcjonalnie)</label><textarea id={fieldId('interaction-description')} name="longDesc" defaultValue={initial.longDesc || ''} rows={4} maxLength={10240} placeholder="Co chcesz zapamiętać?" />
      {type === 'EVENT' && <EventPhotoPicker value={photo} currentPhoto={editing && extra.photoUrl ? { id: initial.id, url: extra.photoUrl, name: extra.photoName } : null} onChange={setPhoto} onBusy={setReadingPhoto} onModeChange={setPhotoMode} />}
      {error && <p className="field-error" role="alert">{error}</p>}
      <button className="primary submit" type="submit" disabled={readingPhoto}>{readingPhoto ? 'Odczytywanie zdjęcia…' : saving ? 'Zapisywanie…' : editing ? 'Zapisz zmiany' : config.label + ' →'}</button>
    </fieldset>
  </form>
}
