import { css } from 'styled-components'

export default css`
.my-quotes-board { width: 90%; margin: 20px auto 50px; display: flex; flex-wrap: wrap; justify-content: space-evenly; gap: 24px; }
.my-quotes-column { flex: 0 1 45%; min-width: 280px; min-height: 500px; display: flex; flex-direction: column; padding: 16px 0 24px; background-color: #80808059; border: 1px solid black; border-radius: 30px; transition: box-shadow 0.15s ease; }
.my-quotes-column.is-drop-target { box-shadow: inset 0 0 0 3px var(--accent); }
.my-quotes-column h4 { margin: 0 0 12px; text-align: center; font-size: 18px; font-weight: 600; }
.my-quotes-column h4 small { margin-left: 6px; font-size: 13px; font-weight: 400; opacity: 0.75; }
.my-quotes-column ul { flex: 1; display: flex; flex-direction: column; justify-content: space-evenly; gap: 12px; list-style: none; margin: 0; padding: 0; }
.my-quotes-column li { flex: none; width: 80%; margin: 0 auto; padding: 5%; box-sizing: border-box; background-color: white; color: #111; border: 1px solid black; border-radius: 30px; cursor: grab; touch-action: none; }
.my-quotes-column li:active { cursor: grabbing; }
.my-quotes-column li.is-dragging { opacity: 0.6; }
.my-quotes-column li.my-quotes-empty { background: none; border-style: dashed; color: inherit; text-align: center; font-size: 14px; opacity: 0.7; cursor: default; }
`
