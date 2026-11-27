import { css } from 'styled-components'

export default css`
.trip-map-section { margin-top: 20px; }
.trip-map-section h4 { margin: 0 0 12px; color: var(--ink); }
.trip-map { height: 420px; width: 100%; border: 1px solid var(--border); border-radius: 14px; background: var(--surface); isolation: isolate; }
.trip-map .leaflet-popup-content-wrapper, .trip-map .leaflet-popup-tip, .trip-map .leaflet-tooltip,
.trip-map .leaflet-control-zoom a, .trip-map .leaflet-control-attribution { background: var(--surface); color: var(--ink); }
.trip-map .leaflet-control-attribution a { color: var(--accent); }
.trip-map .leaflet-control-zoom a { width: 30px; height: 30px; padding: 0; border-radius: 0; }
.place-category-marker-content { display: grid; place-items: center; width: 40px; height: 40px; padding: 7px; background: #fff; border: 2px solid #887453; border-radius: 50%; box-shadow: 0 2px 7px #0004; }
.trip-map .place-category-marker-content img { display: block; width: 100%; height: 100%; object-fit: contain; max-width: none; }
.place-category-marker-fallback { color: #887453; font-size: 22px; }
.place-category-marker:focus-visible { outline: 3px solid var(--accent); outline-offset: 3px; border-radius: 50%; }
@media (max-width: 480px) { .trip-map { height: 260px; } }

.person-browser-card { padding: 22px; min-width: 0; }
.person-browser-card { cursor: pointer; }
.person-browser-card:focus-within { outline: 2px solid var(--accent); outline-offset: 3px; }
.person-open { padding: 0; border: 0; background: transparent; color: inherit; font: inherit; text-align: left; }
.person-open:hover { text-decoration: underline; }
.person-detail-dialog { width: min(960px, calc(100% - 32px)); max-height: calc(100dvh - 32px); overflow-y: auto; }
.person-profile-tabs { display: flex; flex-wrap: wrap; gap: 8px; margin: 20px 0; }
.person-profile-tabs button { padding: 9px 14px; border: 1px solid var(--border); border-radius: 18px; background: var(--surface); color: var(--ink); }
.person-profile-tabs button[aria-current] { background: var(--accent); color: var(--surface); }
.profile-entry { padding: 20px; margin: 16px 0; }
.profile-entry h3 { overflow-wrap: anywhere; }
.profile-photo-form { margin-top: 24px; }
.profile-photo-form fieldset { border: 1px solid var(--border); border-radius: 14px; padding: 18px; min-width: 0; }
.profile-photo-form label { display: grid; gap: 8px; margin-bottom: 14px; }
.person-detail-heading { padding-right: 20px; overflow-wrap: anywhere; }
.person-detail-heading .calendar-avatar { width: 112px; height: 112px; font-size: 32px; }
.person-detail-fields { display: grid; gap: 14px; }
.person-detail-fields dt { color: var(--muted); font-size: 13px; }
.person-detail-fields dd { margin: 4px 0 0; overflow-wrap: anywhere; }
.person-detail-dialog > a { color: var(--accent); }
.person-browser-card .person { gap: 14px; }
.person-browser-card h3 { margin: 0; overflow-wrap: anywhere; }
.person-browser-card .calendar-avatar { width: 64px; height: 64px; flex-shrink: 0; }
.person-browser-card > p, .people-sort-note { color: var(--muted); }
.person-visit-count strong { font-size: 1.5rem; color: var(--ink); }
`
