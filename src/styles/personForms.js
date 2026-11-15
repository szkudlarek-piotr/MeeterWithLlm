import { css } from 'styled-components'

export default css`
.login-dialog:has(.human-form) { width: min(620px, calc(100% - 32px)); max-height: calc(100svh - 40px); overflow-y: auto; }
.human-form { margin-top: 0; }
.form-intro { color: var(--muted); font-size: 14px; margin: 0 0 24px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 20px; }
.form-grid label { display: block; }
.form-grid .full-width { grid-column: 1 / -1; }
.field-hint { display: block; color: var(--muted); font-size: 11px; line-height: 1.6; margin: -10px 0 20px; }
.clique-field { position: relative; }
.clique-input { position: relative; }
.clique-input input { padding-right: 40px; }
.clique-input button { position: absolute; top: 8px; right: 10px; border: 0; background: transparent; color: var(--muted); font-size: 22px; }
.clique-options { position: absolute; top: 76px; left: 0; right: 0; z-index: 6; max-height: 190px; overflow-y: auto; padding: 6px; background: var(--card); border: 1px solid var(--border); border-radius: 12px; box-shadow: 0 12px 24px #0002; }
.clique-options [role='option'] { display: flex; justify-content: space-between; cursor: pointer; padding: 12px; font-size: 13px; border-radius: 7px; }
.clique-options [role='option']:hover, .clique-options .highlighted { background: var(--soft); }
.clique-options p { color: var(--muted); font-size: 12px; padding: 6px; }
.field-error { color: #bd5243; font-size: 12px; }
.relation-note { color: var(--muted); font-size: 12px; line-height: 1.7; background: var(--soft); padding: 14px; border-radius: 10px; }
@media (max-width: 480px) { .form-grid { grid-template-columns: minmax(0, 1fr); } }
`
