import { css } from 'styled-components'

export default css`
.admin-photo-import { padding: 24px; }
.admin-photo-import > p { color: var(--muted); font-size: 13px; line-height: 1.7; }
.import-results { list-style: none; padding: 0; max-height: 420px; overflow: auto; }
.import-results li { display: flex; flex-wrap: wrap; gap: 8px 16px; padding: 12px 0; border-bottom: 1px solid var(--border); font-size: 12px; overflow-wrap: anywhere; }
.admin-tools { display: grid; gap: 20px; }
`
