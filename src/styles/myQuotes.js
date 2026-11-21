import { css } from 'styled-components'

export default css`
.my-quotes-board { width: 90%; margin: 20px auto 50px; display: flex; flex-wrap: wrap; justify-content: space-evenly; gap: 24px; }
.my-quotes-column { flex: 0 1 45%; min-width: 280px; min-height: 500px; display: flex; flex-direction: column; padding: 18px 0 24px; background: var(--card); border: 1px solid var(--border); border-radius: 18px; transition: border-color 0.15s ease, box-shadow 0.15s ease; }
.my-quotes-column.is-drop-target { border-color: var(--accent); box-shadow: 0 0 0 3px var(--glow); }
.my-quotes-column h4 { margin: 0 0 12px; text-align: center; color: var(--ink); font-size: 17px; font-weight: 600; }
.my-quotes-column h4 small { margin-left: 8px; padding: 2px 10px; border-radius: 999px; background: var(--glow); color: var(--accent); font-size: 12px; font-weight: 600; }
.my-quotes-column ul { flex: 1; display: flex; flex-direction: column; justify-content: space-evenly; gap: 10px; list-style: none; margin: 0; padding: 0; }
.my-quotes-column li { flex: none; width: 85%; margin: 0 auto; padding: 12px 16px; box-sizing: border-box; background: var(--surface); color: var(--ink); border: 1px solid var(--border); border-radius: 12px; cursor: grab; touch-action: none; }
.my-quotes-column li:active { cursor: grabbing; }
.my-quotes-column li.is-dragging { opacity: 0.6; }
.my-quotes-column li.my-quotes-empty { background: none; border-style: dashed; color: var(--muted); text-align: center; font-size: 14px; cursor: default; }
`
