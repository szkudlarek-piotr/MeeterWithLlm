import { css } from 'styled-components'

export default css`
.login-dialog:has(.interaction-form) { width: min(720px, calc(100% - 32px)); max-height: calc(100svh - 40px); overflow-y: auto; }
.interaction-form { margin-top: 0; }
.interaction-form fieldset { border: 0; padding: 0; margin: 0; min-width: 0; }
.interaction-form label { display: block; }
.interaction-form textarea { width: 100%; }
.people-toolbar { display: flex; align-items: center; flex-wrap: wrap; gap: 16px; margin-bottom: 20px; }
.people-toolbar label { flex: 1 1 220px; font-size: 13px; }
.people-toolbar input { width: 100%; margin-top: 8px; margin-bottom: 0; }
.news-content { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.7; }
.entry-meta time { display: block; font-size: 12px; color: var(--muted); }
`
