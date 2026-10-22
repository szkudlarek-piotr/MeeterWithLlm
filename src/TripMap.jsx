import { useEffect, useRef, useState } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { placesCategoriesDict } from './PlacesCategoriesDict.js'

const normalizeCategory = category => String(category ?? '').normalize('NFC').trim().replace(/\s+/g, ' ').toLocaleLowerCase('pl-PL')
const categoryIcons = new Map(Object.entries(placesCategoriesDict).map(([category, filename]) => [normalizeCategory(category), filename]))

function categoryMarker(place) {
  const filename = categoryIcons.get(normalizeCategory(place.category))
  if (!filename) return L.circleMarker([place.latitude, place.longitude], {
    radius: 8, color: '#ffffff', weight: 2, fillColor: '#887453', fillOpacity: 1,
  })
  const content = document.createElement('span')
  content.className = 'place-category-marker-content'
  const image = document.createElement('img')
  image.src = `/api/places/category-icons/${encodeURIComponent(filename)}`
  image.alt = ''
  image.addEventListener('error', () => {
    content.textContent = '●'
    content.classList.add('place-category-marker-fallback')
  }, { once: true })
  content.append(image)
  return L.marker([place.latitude, place.longitude], {
    title: place.name, alt: place.name,
    icon: L.divIcon({ html: content, className: 'place-category-marker', iconSize: [40, 40], iconAnchor: [20, 20], popupAnchor: [0, -20], tooltipAnchor: [20, 0] }),
  })
}

const validPoint = place => Number.isFinite(place.latitude) && Number.isFinite(place.longitude)
  && Math.abs(place.latitude) <= 90 && Math.abs(place.longitude) <= 180

export default function TripMap({ places = [], title = 'Miejsca na trasie', emptyMessage = 'Dodaj miejsca ze współrzędnymi, aby zobaczyć mapę podróży.' }) {
  const container = useRef(null)
  const [tileError, setTileError] = useState(false)
  const points = places.filter(validPoint)
  const signature = JSON.stringify(points.map(({ id, name, latitude, longitude, category }) => ({ id, name, latitude, longitude, category })))

  useEffect(() => {
    const locations = JSON.parse(signature)
    if (!locations.length || !container.current) return
    setTileError(false)
    const map = L.map(container.current, { scrollWheelZoom: true })
    const tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    }).addTo(map)
    tiles.on('tileerror', () => setTileError(true))
    locations.forEach(place => {
      const label = document.createElement('span')
      label.textContent = place.name
      categoryMarker(place).addTo(map).bindTooltip(label).bindPopup(label.cloneNode(true))
    })
    const bounds = L.latLngBounds(locations.map(place => [place.latitude, place.longitude]))
    const fit = () => {
      if (!container.current?.clientWidth || !container.current?.clientHeight) return
      map.invalidateSize()
      map.fitBounds(bounds, { padding: [35, 35], maxZoom: 13, animate: false })
    }
    const observer = new ResizeObserver(fit)
    observer.observe(container.current)
    fit()
    return () => { observer.disconnect(); map.remove() }
  }, [signature])

  return <section className="trip-map-section" aria-label={title}>
    <h4>{title}</h4>
    {points.length ? <>
      <div ref={container} className="trip-map" role="region" aria-label={title} />
      {tileError && <p className="field-hint" role="status">Nie udało się pobrać części mapy. Miejsca znajdziesz na liście poniżej.</p>}
    </> : <p className="field-hint">{emptyMessage}</p>}
    {places.length > 0 && <ul className="trip-map-places">{places.map(place => <li key={place.id}>{place.name}</li>)}</ul>}
  </section>
}
