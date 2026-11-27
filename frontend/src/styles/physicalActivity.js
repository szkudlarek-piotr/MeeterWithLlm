import { css } from 'styled-components'

export default css`
.activity-stats { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; margin-bottom: 24px; }
.activity-stat { display: flex; flex-direction: column; gap: 8px; padding: 20px 22px; border-radius: 18px; background: var(--soft); }
.activity-stat strong { font-family: 'Manrope', sans-serif; font-size: clamp(30px, 3.4vw, 44px); font-weight: 600; line-height: 1; letter-spacing: -0.04em; color: var(--accent); }
.activity-stat span { font-size: 12px; color: var(--muted); }
.activity-layout { display: grid; grid-template-columns: minmax(0, 360px) minmax(0, 1fr); gap: 24px; align-items: start; }
.activity-form { position: sticky; top: 110px; padding: 24px; }
.activity-form h3 { margin: 0 0 20px; font-size: 16px; font-weight: 600; color: var(--ink); }
.activity-form fieldset { border: 0; padding: 0; margin: 0; min-width: 0; }
.activity-field { display: grid; gap: 8px; margin-bottom: 16px; }
.activity-field label { font-size: 12px; color: var(--muted); }
.activity-form input, .activity-form textarea { width: 100%; margin: 0; padding: 12px 14px; font: inherit; font-size: 13px; color: var(--ink); background: var(--surface); border: 1px solid var(--border); border-radius: 12px; }
.activity-form input:focus-visible, .activity-form textarea:focus-visible { outline: 3px solid #a4b78c; outline-offset: 2px; border-color: var(--accent); }
.activity-form .primary.submit { width: 100%; margin-top: 4px; }
.activity-form .text-button.activity-cancel { display: block; width: 100%; padding: 14px 0 0; text-align: center; }
.activity-list { display: grid; gap: 12px; min-width: 0; }
.activity-list > p { margin: 0; padding: 28px; text-align: center; font-size: 13px; color: var(--muted); border: 1px dashed var(--border); border-radius: 18px; }
.activity-row { display: grid; grid-template-columns: 42px minmax(0, 1fr) auto; gap: 16px; align-items: start; padding: 18px 20px; }
.activity-icon { display: grid; place-items: center; width: 42px; height: 42px; border-radius: 14px; background: var(--soft); color: var(--accent); font-size: 18px; }
.activity-row-body { min-width: 0; }
.activity-row-body strong { display: block; font-size: 14px; color: var(--ink); overflow-wrap: anywhere; }
.activity-row-body small { display: block; margin-top: 4px; font-size: 11px; color: var(--muted); }
.activity-row-body p { margin: 10px 0 0; font-size: 13px; line-height: 1.6; color: var(--muted); white-space: pre-wrap; overflow-wrap: anywhere; }
.activity-row-actions { display: flex; gap: 14px; }
.activity-row-actions .text-button { padding: 0; }
@media (max-width: 900px) {
  .activity-layout { grid-template-columns: minmax(0, 1fr); }
  .activity-form { position: static; }
}
@media (max-width: 700px) {
  .activity-stats { gap: 10px; }
  .activity-stat { padding: 16px; }
  .activity-row { grid-template-columns: 36px minmax(0, 1fr); gap: 12px; padding: 16px; }
  .activity-icon { width: 36px; height: 36px; border-radius: 12px; }
  .activity-row-actions { grid-column: 2; }
}
`
