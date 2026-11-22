import { css } from 'styled-components'

export default css`
.calendar-layout { grid-template-columns: 245px minmax(0, 1fr); }
.calendar-month-card { padding: clamp(14px, 2vw, 28px); }
.memory-calendar-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 15px; margin-bottom: 25px; }
.memory-calendar-toolbar .eyebrow { font-size: 8px; letter-spacing: 1.8px; }
.memory-calendar-toolbar h3 { margin: 10px 0 5px; font-size: 25px; font-family: 'Manrope', sans-serif; text-transform: capitalize; letter-spacing: -.8px; }
.calendar-subtitle { color: var(--muted); font-size: 11px; margin: 0; }
.calendar-controls { display: flex; gap: 6px; }
.calendar-controls button { padding: 9px 12px; border: 1px solid var(--border); border-radius: 10px; background: var(--surface); color: var(--ink); font-size: 12px; }
.memory-calendar-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 7px; }
.memory-day { display: flex; flex-direction: column; align-items: stretch; text-align: left; min-height: 132px; padding: 11px; border: 1px solid transparent; border-radius: 12px; background: var(--surface); color: var(--ink); overflow: hidden; }
.memory-day.filled { border-color: var(--border); background: var(--card); }
.memory-day:hover { background: var(--soft); }
.memory-day.selected { border-color: var(--accent); box-shadow: inset 0 0 0 1px var(--accent); background: var(--soft); }
.memory-day-number { display: flex; justify-content: space-between; align-items: center; font-weight: 500; font-size: 12px; }
.memory-day-number small { color: var(--muted); font-size: 9px; }
.memory-day.today .memory-day-number { color: var(--accent); font-weight: 800; }
.memory-day.today .memory-day-number::after { content: ''; width: 5px; height: 5px; background: var(--accent); border-radius: 50%; margin-left: 4px; }
.calendar-people { display: flex; align-items: center; margin: 13px 0 7px; }
.calendar-avatar { width: 28px; height: 28px; flex-shrink: 0; display: inline-grid; place-items: center; background: #dce7cb; color: #3a573b; border-radius: 50%; border: 2px solid var(--card); overflow: hidden; font-size: 9px; font-weight: 700; }
.calendar-avatar img { width: 100%; height: 100%; object-fit: cover; }
.sample-person { display: block; color: var(--muted); font-size: 9px; margin-top: 3px; }
.person > .calendar-avatar { width: 36px; height: 36px; font-size: 11px; }
.participant-chips .calendar-avatar { width: 24px; height: 24px; margin-left: -5px; }
.calendar-people .calendar-avatar + .calendar-avatar { margin-left: -9px; }
.more-people { font-size: 9px; color: var(--muted); padding-left: 4px; }
.day-preview { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 10px; margin-bottom: 8px; }
.day-preview-list { white-space: normal; overflow-wrap: anywhere; }
.day-types { display: flex; gap: 3px; margin-top: auto; }
.type-mark { display: inline-grid; place-items: center; width: 20px; height: 20px; border-radius: 6px; font-size: 11px; font-style: normal; }
.type-meeting { background: #dce9d4; color: #41663a; }
.type-visit { background: #f7e2cf; color: #915620; }
.type-event { background: #dce7f4; color: #466b98; }
.type-trip { background: #e7e1f2; color: #73608c; }
.type-flatmate { background: #e1e7df; color: #596852; }
.type-wedding { background: #f4dfe8; color: #a15c76; }
.calendar-legend { display: flex; flex-wrap: wrap; gap: 12px; margin-top: 20px; padding-top: 18px; border-top: 1px solid var(--border); }
.calendar-legend > span { display: inline-flex; align-items: center; gap: 6px; color: var(--muted); font-size: 10px; }
.calendar-day-panel { margin: 35px 0; }
.calendar-day-dialog { width: min(1080px, calc(100vw - 32px)); max-height: calc(100dvh - 32px); box-sizing: border-box; overflow-y: auto; padding: clamp(20px, 4vw, 36px); border: 1px solid var(--border); border-radius: 22px; background: var(--surface); color: var(--ink); }
.calendar-day-dialog::backdrop { background: rgb(15 24 20 / 60%); backdrop-filter: blur(4px); }
.calendar-day-dialog .day-panel-heading { padding-right: 30px; margin-bottom: 20px; }
.calendar-day-dialog h2 { font-size: clamp(21px, 4vw, 28px); margin: 8px 0; }
.calendar-day-dialog .memory-detail { margin-top: 20px; }
.calendar-add-actions { display: flex; flex-wrap: wrap; gap: 8px; margin: 0 0 8px; }
.participant-editor { margin: 20px 0; }
.participant-editor fieldset { border: 0; padding: 0; margin: 0; min-width: 0; }
.participant-editor legend { font-size: 12px; font-weight: 700; }
.participant-editor .calendar-detail-people { margin: 12px 0 18px; }
.participant-editor form { display: grid; gap: 12px; }
.participant-editor form > button { justify-self: start; }
.participant-remove { border: 1px solid var(--border); background: var(--surface); color: var(--ink); border-radius: 8px; padding: 5px 8px; font-size: 10px; }
.participant-remove:disabled { opacity: .5; }
.participant-notice { font-size: 12px; color: var(--muted); }
.day-panel-heading { display: flex; justify-content: space-between; gap: 15px; align-items: center; margin-bottom: 20px; }
.day-panel-heading .eyebrow { font-size: 8px; letter-spacing: 1.8px; }
.day-panel-heading h3 { font-family: 'Manrope', sans-serif; font-size: 24px; font-weight: 500; margin: 10px 0; letter-spacing: -.6px; }
.day-panel-heading button { font-size: 11px; }
.calendar-empty-hint { color: var(--muted); font-size: 13px; line-height: 1.7; }
.memory-detail { padding: 24px; margin-bottom: 16px; }
.memory-detail-heading { display: flex; align-items: center; gap: 8px; color: var(--muted); font-size: 11px; }
.memory-detail-heading small { margin-left: auto; }
.memory-detail h3 { font-family: 'Manrope', sans-serif; font-size: 22px; font-weight: 500; letter-spacing: -.5px; margin: 18px 0; overflow-wrap: anywhere; }
.memory-location, .wedding-couple { color: var(--muted); font-size: 12px; line-height: 1.7; }
.calendar-detail-people { display: flex; flex-wrap: wrap; gap: 12px 20px; margin: 20px 0; }
.calendar-person { display: inline-flex; gap: 7px; align-items: center; font-size: 11px; }
.memory-description { color: var(--muted); line-height: 1.8; font-size: 14px; white-space: pre-wrap; overflow-wrap: anywhere; }
.memory-photo { display: block; width: 100%; max-height: 350px; object-fit: cover; border-radius: 12px; margin-top: 18px; }
@media (max-width: 1150px) { .calendar-layout { grid-template-columns: 215px minmax(0, 1fr); } .memory-day { min-height: 110px; padding: 7px; } .calendar-people .calendar-avatar:nth-child(3) { display: none; } }
@media (max-width: 700px) { .calendar-layout { grid-template-columns: minmax(0, 1fr); } .memory-calendar-grid { gap: 3px; } .memory-day { min-height: 88px; padding: 6px 4px; border-radius: 8px; } .calendar-people { margin: 8px 0 5px; } .calendar-avatar { width: 24px; height: 24px; font-size: 8px; } .calendar-people .calendar-avatar:nth-child(2) { display: none; } .day-preview { font-size: 8px; } .day-types { gap: 1px; flex-wrap: wrap; } .day-types .type-mark { width: 13px; height: 14px; font-size: 9px; } .memory-calendar-toolbar { flex-wrap: wrap; } .memory-calendar-toolbar h3 { font-size: 22px; } .day-panel-heading { align-items: start; flex-direction: column; } .memory-detail { padding: 18px; } }
.calendar { padding: 24px; }
.calendar-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 20px; }
.calendar-toolbar h3 { text-transform: capitalize; font-size: 18px; }
.calendar-toolbar button { border: 1px solid var(--border); border-radius: 10px; padding: 7px 12px; margin-left: 6px; color: var(--ink); background: var(--surface); }
.calendar-grid { display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 6px; }
.weekday { text-align: center; font-size: 10px; color: var(--muted); padding-bottom: 14px; }
.calendar-day { position: relative; min-height: 55px; border: 0; border-radius: 10px; background: var(--surface); color: var(--ink); }
.calendar-day:hover, .calendar-day.selected { background: var(--soft); outline: 1px solid var(--accent); }
.day-dot { position: absolute; bottom: 7px; left: calc(50% - 2px); width: 4px; height: 4px; border-radius: 50%; background: var(--accent); }
.day-detail { padding: 24px; margin-top: 18px; }
.day-detail p { color: var(--muted); font-size: 13px; }
`
