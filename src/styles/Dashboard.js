import { css } from 'styled-components'

export default css`
:root { --card: #fffefa; --soft: #eaf0e4; }
:root[data-theme='dark'] { --card: #1e2a23; --soft: #2b3c30; }
.app-shell { min-height: 100svh; }
.app-shell > .topbar { padding: 18px 20px; border-bottom: 1px solid var(--border); background: var(--card); position: sticky; top: 0; z-index: 5; }
.dashboard { width: 100%; margin: 0; display: grid; grid-template-columns: 215px minmax(0, 1fr) 215px; gap: 24px; padding: 24px 20px; align-items: start; }
.sidebar { position: sticky; top: 110px; }
.profile { display: flex; gap: 12px; align-items: center; margin: 0 0 28px; min-width: 0; }
.profile > div { min-width: 0; }
.profile strong { display: block; font-size: 14px; }
.profile small { display: block; color: var(--muted); font-size: 11px; margin-top: 4px; overflow: hidden; text-overflow: ellipsis; }
.avatar { flex-shrink: 0; width: 44px; height: 44px; border-radius: 16px; background: #dce7cb; color: #3a573b; display: inline-grid; place-items: center; font-size: 18px; font-weight: 700; }
.avatar.small { width: 36px; height: 36px; border-radius: 13px; font-size: 13px; }
.nav-item { display: flex; gap: 13px; align-items: center; width: 100%; border: 0; background: transparent; color: var(--ink); padding: 13px 15px; text-align: left; border-radius: 12px; margin: 3px 0; font-size: 13px; }
.nav-item > span:first-child { width: 22px; text-align: center; font-size: 21px; color: var(--accent); }
.nav-item:hover, .nav-item.active { background: var(--soft); }
.nav-item.active { font-weight: 700; }
.nav-plus { margin-left: auto; color: var(--muted); }
.sidebar-heading { font-size: 9px; letter-spacing: 1.7px; color: var(--muted); margin: 30px 15px 12px; }
.sidebar-note { display: flex; gap: 15px; align-items: center; color: var(--muted); margin: 30px 15px; border-top: 1px solid var(--border); padding-top: 22px; font-size: 12px; line-height: 1.7; }
.sidebar-note > span { font-size: 30px; color: #b29166; }
.friend-search { display: flex; align-items: center; gap: 12px; border: 1px solid var(--border); border-radius: 15px; background: var(--card); padding: 0 18px; margin: 0; }
.friend-search > span { font-size: 30px; color: var(--muted); }
.friend-search input { border: 0; margin: 0; padding: 16px 0; font-size: 13px; }
.search-results { position: absolute; top: calc(100% + 8px); left: 0; right: 0; background: var(--card); border: 1px solid var(--border); border-radius: 15px; box-shadow: 0 12px 30px #0002; padding: 18px; }
.search-results > small { color: var(--muted); }
.page-heading { display: flex; justify-content: space-between; align-items: start; gap: 10px; margin: 35px 0 28px; }
.page-heading .eyebrow { font-size: 9px; letter-spacing: 2px; margin: 0 0 12px; }
.page-heading h2 { margin: 0; font-size: clamp(24px, 2.3vw, 34px); }
.page-heading p:not(.eyebrow) { color: var(--muted); font-size: 13px; line-height: 1.6; }
.demo-badge { font-size: 9px; white-space: nowrap; padding: 6px 9px; border: 1px solid var(--border); border-radius: 20px; color: var(--muted); }
.card { background: var(--card); border: 1px solid var(--border); border-radius: 18px; }
.composer { display: flex; align-items: center; gap: 12px; padding: 18px; }
.composer > button { border: 0; background: var(--soft); color: var(--muted); padding: 14px; border-radius: 12px; flex: 1; text-align: left; font-size: 12px; }
.composer > .compose-add { flex: 0; font-size: 20px; color: var(--ink); }
.feed-label { font-size: 9px; letter-spacing: 1.8px; color: var(--muted); margin: 28px 0 15px; display: flex; justify-content: space-between; }
.feed-card { padding: 25px; margin-bottom: 18px; overflow-wrap: anywhere; }
.entry-meta { display: flex; align-items: center; gap: 12px; }
.entry-icon { display: grid; place-items: center; height: 42px; width: 42px; border-radius: 14px; background: var(--soft); font-size: 22px; }
.entry-meta strong { font-size: 12px; }
.entry-meta small { display: block; font-size: 10px; margin-top: 5px; color: var(--muted); }
.feed-card h3 { font-family: 'Manrope', sans-serif; font-size: 23px; line-height: 1.4; font-weight: 600; letter-spacing: -.7px; }
.feed-card p { color: var(--muted); line-height: 1.7; font-size: 13px; white-space: pre-wrap; }
.entry-footer { border-top: 1px solid var(--border); padding-top: 16px; margin-top: 24px; font-size: 10px; color: var(--muted); }
.people-card { padding: 22px; }
.section-title { display: flex; justify-content: space-between; align-items: center; }
.section-title h3 { font-size: 14px; margin: 0 0 14px; }
.section-title > span { font-size: 12px; color: var(--muted); }
.person { display: flex; align-items: center; gap: 11px; padding: 10px 0; font-size: 12px; overflow-wrap: anywhere; }
.text-button { border: 0; padding: 15px 0 0; background: transparent; color: var(--accent); font-weight: 600; font-size: 12px; text-align: left; }
.connection-note { padding: 30px 23px; margin-top: 22px; border-radius: 18px; background: var(--soft); }
.connection-note > span { font-size: 30px; }
.connection-note h3 { font-size: 21px; line-height: 1.4; font-weight: 500; letter-spacing: -.6px; }
.connection-note p, .data-note { color: var(--muted); font-size: 12px; line-height: 1.8; }
.data-note { padding: 8px 12px; font-size: 10px; }
textarea { font: inherit; resize: vertical; border: 1px solid var(--border); border-radius: 10px; background: transparent; color: var(--ink); padding: 12px; margin-bottom: 20px; }
textarea:focus-visible { outline: 3px solid #a4b78c; outline-offset: 4px; }
select { width: 100%; font: inherit; border: 1px solid var(--border); border-radius: 10px; background: var(--surface); color: var(--ink); padding: 13px 14px; margin-bottom: 20px; }
select:focus-visible { outline: 3px solid #a4b78c; outline-offset: 4px; }
.account-password { padding: 8px; }
button:disabled { cursor: wait; opacity: .6; }
`
