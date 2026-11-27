import { css } from 'styled-components'

export default css`
.welcome { min-height: 100svh; padding: 40px clamp(24px, 7vw, 112px); display: flex; flex-direction: column; background: radial-gradient(ellipse at 90% 40%, var(--glow) 0, transparent 55%); }
.brand { display: inline-flex; align-items: center; align-self: flex-start; gap: 10px; color: inherit; text-decoration: none; font-size: 27px; font-weight: 700; letter-spacing: -1px; }
.brand-mark { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 12px; background: #345d48; color: #f5f4ee; font-size: 26px; }
.dot { color: #d58b57; margin-left: -10px; }
.eyebrow { color: #718272; font-size: 11px; font-weight: 700; letter-spacing: 3px; }
h1 { font-family: 'Manrope', sans-serif; font-size: clamp(40px, 6vw, 84px); font-weight: 600; line-height: 1.12; letter-spacing: -0.055em; margin: 28px 0; }
h1 span { color: #668563; }
.description { max-width: 450px; color: var(--muted); font-size: 17px; line-height: 1.8; }
.status { display: inline-flex; align-items: center; gap: 10px; margin-top: 30px; padding: 12px 17px; border: 1px solid var(--border); border-radius: 30px; font-size: 12px; }
.status span { height: 7px; width: 7px; border-radius: 50%; background: #719269; }
footer { color: #8c9489; font-size: 12px; padding-bottom: 5px; }

:root[data-theme='dark'] {
  color-scheme: dark;
  --surface: #151e1a;
  --ink: #e6eddf;
  --muted: #a1afa3;
  --border: #36463b;
  --accent: #b7cf9e;
  --glow: #283c2e;
}
[data-theme='dark'] h1 span { color: #b7cf9e; }
[data-theme='dark'] .eyebrow { color: #a4b59e; }
.topbar, .actions { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
button, input { font: inherit; }
button { cursor: pointer; }
button:focus-visible, a:focus-visible, input:focus-visible { outline: 3px solid #a4b78c; outline-offset: 4px; }
.theme-toggle { display: inline-flex; align-items: center; gap: 8px; background: transparent; border: 0; color: var(--ink); padding: 10px; font-size: 13px; }
.theme-toggle > span:first-child { font-size: 22px; }
.public-quotes-toggle { padding: 8px 12px; border: 1px solid var(--border); border-radius: 18px; background: var(--surface); color: var(--ink); font-size: 13px; }
.public-quotes-toggle[aria-pressed="true"] { background: var(--accent); color: var(--surface); }
.app-shell .actions { flex-wrap: wrap; justify-content: flex-end; }
.welcome .actions { flex-wrap: wrap; justify-content: flex-end; }
.topbar .actions {
  gap: 8px;
}
.topbar .actions > button {
  min-height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 12px;
  font-size: 13px;
  line-height: 1.2;
  white-space: nowrap;
}
.topbar .actions > .primary {
  padding-inline: 20px;
}
.primary { display: inline-flex; justify-content: center; align-items: center; gap: 20px; border: 0; border-radius: 30px; padding: 13px 22px; background: var(--accent); color: var(--surface); font-size: 13px; font-weight: 700; }
.primary:hover { filter: brightness(1.12); }
.login-dialog { position: fixed; width: min(440px, calc(100% - 32px)); padding: 38px; border: 1px solid var(--border); border-radius: 24px; color: var(--ink); background: var(--surface); box-shadow: 0 24px 100px #0003; }
.login-dialog::backdrop { background: #0d1918a6; backdrop-filter: blur(6px); }
.close-dialog { position: absolute; top: 12px; right: 16px; background: transparent; border: 0; color: var(--muted); font-size: 28px; }
h2 { font-family: 'Manrope', sans-serif; font-size: 30px; letter-spacing: -1px; line-height: 1.2; }
.login-description, .demo-message { color: var(--muted); font-size: 14px; line-height: 1.6; }
.demo-message { max-width: 480px; overflow-wrap: anywhere; }
form { display: flex; flex-direction: column; margin-top: 26px; }
label { margin-bottom: 8px; font-size: 13px; font-weight: 600; }
input { min-width: 0; width: 100%; border: 1px solid var(--border); border-radius: 10px; background: transparent; color: var(--ink); padding: 13px 14px; margin-bottom: 20px; }
.demo-note { font-size: 12px; color: var(--muted); line-height: 1.6; margin: 0 0 20px; }
.submit { width: 100%; }
@media (max-width: 600px) {
  .welcome { padding-top: 24px; }
  .topbar .actions { gap: 4px; }
  .topbar .actions > button { padding-inline: 8px; }
  .topbar .actions > .primary { padding-inline: 12px; }
  .theme-toggle > span:last-child { display: none; }
  .primary { padding: 12px 16px; gap: 8px; }
  .login-dialog { padding: 30px 24px; }
}
`
