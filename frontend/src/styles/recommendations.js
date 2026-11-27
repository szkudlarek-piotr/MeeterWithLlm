import { css } from 'styled-components'

export default css`
.participant-recommendations { margin: 1rem 0; padding: 1rem; border: 1px solid var(--border, #8886); border-radius: 12px; }
.participant-recommendations label small { font-weight: normal; opacity: .7; }
.recommendation-controls { display: flex; gap: .65rem; flex-wrap: wrap; align-items: center; }
.recommendation-controls select { flex: 1 1 250px; min-width: 0; max-width: 100%; margin-bottom: 0; padding: .65rem; background: var(--surface); color: var(--ink); border: 1px solid var(--border); border-radius: 8px; }
.recommendation-controls option { background: var(--surface); color: var(--ink); }
.recommendation-controls > button, .recommendation-people button { padding: .65rem .8rem; border: 1px solid var(--border); border-radius: 10px; background: var(--soft); color: var(--ink); }
.recommendation-controls > button:disabled { opacity: .55; cursor: default; }
.participant-recommendations > label { display: block; }
.recommendation-people { display: flex; flex-wrap: wrap; gap: .5rem; margin-top: .75rem; }
.recommendation-people button { display: flex; align-items: center; gap: .5rem; text-align: left; }
`
