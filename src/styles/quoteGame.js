import { css } from 'styled-components'

export default css`
.quote-game > .card { padding: clamp(16px, 3vw, 28px); margin-bottom: 20px; }
.game-setup h3, .game-leaderboard h3, .game-history h3 { margin-top: 0; }
.game-visit-options { display: grid; gap: 8px; margin-bottom: 20px; }
.game-visit-options button { border: 1px solid var(--border); background: var(--card); color: var(--ink); padding: 12px; text-align: left; border-radius: 10px; }
.game-visit-options button[aria-pressed="true"] { border-color: var(--accent); background: var(--soft); }
.game-visit-options small, .game-history-row small { display: block; margin-top: 6px; color: var(--muted); }
.game-play progress { width: 100%; height: 8px; margin: 16px 0 24px; accent-color: var(--accent); }
.game-play blockquote { font-size: clamp(20px, 3vw, 28px); line-height: 1.5; margin: 20px 0 28px; white-space: pre-wrap; overflow-wrap: break-word; }
.game-play fieldset { border: 0; padding: 0; min-width: 0; }
.game-play legend { font-weight: 600; margin-bottom: 14px; }
.game-play .submit { margin-top: 20px; }
.game-play > .field-hint { margin: 16px 0; }
.game-candidates { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(200px, 100%), 1fr)); gap: 10px; }
.game-candidates label { display: flex; align-items: center; gap: 10px; padding: 14px; border: 1px solid var(--border); border-radius: 12px; background: var(--card); color: var(--ink); font-size: 14px; cursor: pointer; }
.game-candidates label.chosen { background: var(--soft); border-color: var(--accent); }
.game-candidates input { width: auto; margin: 0; accent-color: var(--accent); }
.game-clock { display: inline-block; padding: 10px 18px; border-radius: 24px; background: var(--soft); color: var(--ink); font-size: 22px; font-weight: 700; font-variant-numeric: tabular-nums; }
.game-clock.urgent { color: #c34432; }
.game-final { padding: 18px 0; }
.game-final > strong { display: block; font-size: 36px; color: var(--ink); }
.game-leaderboard table { border-collapse: collapse; width: 100%; font-size: 13px; }
.game-leaderboard th, .game-leaderboard td { text-align: left; padding: 12px 10px; border-bottom: 1px solid var(--border); white-space: nowrap; }
.game-leaderboard th { color: var(--muted); font-weight: 500; }
.game-history-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 16px 0; border-bottom: 1px solid var(--border); font-size: 13px; }
.event-photo-options { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 10px; }
.event-photo-options button { padding: 8px; border: 2px solid var(--border); border-radius: 12px; background: var(--card); color: var(--ink); text-align: left; }
.event-photo-options button[aria-pressed="true"] { border-color: var(--accent); }
.event-photo-options img { width: 100%; height: 90px; object-fit: cover; border-radius: 6px; }
.event-photo-options span { display: block; margin-top: 6px; font-size: 12px; overflow-wrap: anywhere; }
.event-photo-pages { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin: 12px 0; }
.event-photo-pages button { background: var(--soft); color: var(--ink); border: 1px solid var(--border); border-radius: 8px; padding: 8px; }
.entity-picker { position: relative; min-width: 0; margin-bottom: 20px; }
.entity-picker > input { margin-bottom: 0; }
.quote-author-selection { margin-bottom: 20px; }
.quote-author-selection > p { margin: 0 0 8px; font-weight: 700; }
.quote-author-selection > div { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 12px; padding: 12px; border: 1px solid var(--border); border-radius: 10px; background: var(--soft); color: var(--ink); }
.quote-author-selection button { color: var(--ink); }
.entity-options { position: absolute; z-index: 8; top: 75px; left: 0; right: 0; max-height: 210px; overflow-y: auto; padding: 6px; border: 1px solid var(--border); border-radius: 12px; background: var(--card); box-shadow: 0 10px 24px #0002; }
.entity-options button { display: block; width: 100%; padding: 12px; text-align: left; color: var(--ink); background: transparent; border: 0; border-radius: 8px; font-size: 13px; }
.entity-options button:hover, .entity-options .highlighted { background: var(--soft); }
.entity-options button.person-option { display: flex; align-items: center; gap: 12px; }
.entity-options .person-option .calendar-avatar { width: 40px; height: 40px; font-size: 12px; }
.selected-person { display: flex; align-items: center; gap: 10px; margin-top: 10px; }
.selected-person > .text-button { margin-left: auto; }
.selected-entities .calendar-avatar { width: 32px; height: 32px; font-size: 10px; }
.entity-options p { padding: 8px; font-size: 12px; color: var(--muted); }
.selected-entities, .participant-chips { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 10px; }
.selected-entities > span, .participant-chips > span { display: inline-flex; align-items: center; gap: 8px; font-size: 11px; border-radius: 20px; padding: 6px 10px; background: var(--soft); color: var(--ink); }
.selected-entities button { background: transparent; border: 0; color: var(--ink); padding: 0 2px; font-size: 18px; }
.participant-help { margin-top: 0; }
.interaction-form .checkbox-label { display: flex; align-items: center; gap: 10px; margin-bottom: 22px; font-size: 13px; }
.checkbox-label input { width: auto; margin: 0; accent-color: var(--accent); }
.save-notice { padding: 12px 16px; background: var(--soft); border-radius: 12px; font-size: 13px; overflow-wrap: anywhere; }
.loading-message { font-size: 13px; color: var(--muted); }
.empty-feed { padding: 30px; }
.empty-feed h3 { font-size: 23px; font-weight: 500; }
.empty-feed p { font-size: 14px; line-height: 1.7; color: var(--muted); }
.load-more { margin: 10px 0 25px; }
`
