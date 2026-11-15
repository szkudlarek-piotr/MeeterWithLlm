import { css } from 'styled-components'

export default css`
:root {
  color-scheme: light;
  --surface: #f5f4ee;
  --ink: #24382f;
  --muted: #798078;
  --border: #dce1d5;
  --accent: #345d48;
  --glow: #e3eadc;
  font-family: 'DM Sans', sans-serif;
  color: var(--ink);
  background: var(--surface);
  font-synthesis: none;
  text-rendering: optimizeLegibility;
  -webkit-font-smoothing: antialiased;
}
* { box-sizing: border-box; }
body { margin: 0; }
`
