import { css } from 'styled-components'

export default css`
.my-quotes-board { width: 90%; margin: 20px auto 50px; display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 320px), 1fr)); gap: 24px; justify-content: space-evenly; }
.my-quotes-column { min-height: 500px; background-color: #80808059; border: 1px solid black; border-radius: 30px; padding: 16px 0 24px; transition: background-color 0.15s ease, box-shadow 0.15s ease; }
.my-quotes-column.is-drop-target { box-shadow: inset 0 0 0 3px var(--accent); }
.my-quotes-column h4 { margin: 0 0 12px; text-align: center; font-size: 18px; font-weight: 600; }
.my-quotes-column h4 small { margin-left: 6px; font-size: 13px; font-weight: 400; opacity: 0.75; }
.my-quotes-column ul { list-style: none; margin: 0; padding: 0; }
.my-quotes-column li { display: grid; gap: 8px; width: 80%; margin: 10px auto 0; padding: 5%; background-color: white; color: #111; border: 1px solid black; border-radius: 30px; box-sizing: border-box; cursor: grab; }
.my-quotes-column li:last-child { margin-bottom: 10px; }
.my-quotes-column li:active { cursor: grabbing; }
.my-quotes-column li .text-button { justify-self: end; font-size: 13px; color: #111; }
.my-quotes-empty { width: 80%; margin: 10px auto 0; padding: 5%; border: 1px dashed black; border-radius: 30px; box-sizing: border-box; text-align: center; font-size: 14px; opacity: 0.7; }
`
