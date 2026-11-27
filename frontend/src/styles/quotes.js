import { css } from 'styled-components'

export default css`
.person-clique-icon { object-fit: contain; vertical-align: middle; margin-inline-end: 8px; border-radius: 6px; }
.person-browser-card { text-align: center; }
.person-browser-card .person { flex-direction: column; }
.person-browser-card .person-open { text-align: center; }
.person-card-clique { display: flex; flex-direction: column; align-items: center; gap: 6px; }
.person-card-clique .person-clique-icon { object-fit: cover; margin: 0; border-radius: 50%; }
.person-public-quote { border-top: 1px solid var(--border); margin-top: 18px; padding-top: 16px; }
.person-public-quote, .quote-content { text-align: center; }
.quote-vote { display: flex; align-items: center; justify-content: center; gap: 12px; margin: 12px 0 4px; }
.quote-vote button { min-width: 36px; min-height: 36px; border: 1px solid var(--border); border-radius: 8px; background: var(--soft); color: var(--ink); }
.quote-vote button[aria-pressed="true"] { background: var(--accent); color: var(--surface); opacity: 1; }
.quote-vote button:disabled { cursor: default; }
.quote-vote[aria-busy="true"] button:disabled { cursor: wait; }
.person-detail-dialog:has(.quote-vote) { overflow-anchor: none; }
.quote-vote span { min-width: 3ch; font-variant-numeric: tabular-nums; }
.quote-content { hyphens: auto; -webkit-hyphens: auto; word-break: normal; overflow-wrap: break-word; }
.quote-content { white-space: pre-line; }
.quote-content pre { white-space: pre-wrap; }
.quote-content > :first-child { margin-top: 0; }
.quote-content > :last-child { margin-bottom: 0; }
.quote-content ul, .quote-content ol { list-style-position: inside; padding-inline-start: 0; }
.person-public-quote small { color: var(--muted); font-size: 10px; letter-spacing: 1px; }
.person-public-quote blockquote { margin: 10px 0 0; white-space: pre-wrap; overflow-wrap: break-word; max-height: 12rem; overflow: auto; line-height: 1.6; }
`
