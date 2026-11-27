import { css } from 'styled-components'

export default css`
.cliques-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.clique-card { min-width: 0; padding: 18px; }
.clique-card-heading { display: flex; flex-direction: column; align-items: center; gap: 12px; text-align: center; }
.clique-card-heading h3 { margin: 0; max-width: 100%; font-size: 15px; overflow-wrap: anywhere; }
.clique-cover { width: 96px; height: 96px; flex: 0 0 96px; background: var(--soft); border-radius: 14px; overflow: hidden; }
.clique-cover > img { width: 100%; height: 100%; object-fit: contain; display: block; }
.clique-cover-placeholder { height: 100%; display: grid; place-items: center; color: var(--muted); }
.clique-cover-placeholder > span { font-size: 40px; line-height: 1; }
.clique-card-content { padding-top: 16px; }
.clique-members { list-style: none; padding: 0; margin: 0; display: flex; flex-wrap: wrap; justify-content: space-evenly; gap: 6px; }
.clique-members li { display: flex; }
.clique-members .calendar-avatar { width: 28px; height: 28px; font-size: 9px; }
.clique-member-open { display: flex; padding: 4px; border: 0; border-radius: 50%; background: transparent; color: inherit; cursor: pointer; transition: box-shadow 160ms ease, background 160ms ease; }
.clique-member-open:hover, .clique-member-open:focus-visible { background: var(--glow); box-shadow: 0 0 0 2px var(--accent), 0 0 16px var(--accent); }
.clique-member-open:focus-visible { outline: 2px solid var(--accent); outline-offset: 4px; }
@media (prefers-reduced-motion: reduce) { .clique-member-open { transition: none; } }
@media (max-width: 900px) { .cliques-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 700px) { .cliques-grid { grid-template-columns: minmax(0, 1fr); } }
`
