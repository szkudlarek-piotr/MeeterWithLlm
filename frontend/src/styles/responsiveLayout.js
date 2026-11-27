import { css } from 'styled-components'

export default css`
@media (max-width: 1150px) {
  .dashboard { grid-template-columns: 200px minmax(0, 1fr); gap: 20px; padding: 20px 16px; }
}
@media (max-width: 700px) {
  .app-shell > .topbar { padding: 15px 18px; }
  .dashboard { grid-template-columns: minmax(0, 1fr); padding: 18px 12px; gap: 20px; }
  .sidebar { position: static; }
  .profile, .sidebar-note, .sidebar-heading { display: none; }
  .sidebar nav { display: flex; overflow-x: auto; gap: 6px; padding-bottom: 8px; }
  .nav-item { flex-shrink: 0; width: auto; white-space: nowrap; padding: 9px 12px; font-size: 12px; }
  .nav-plus { display: none; }
  .page-heading { margin-top: 26px; }
  .page-heading h2 { font-size: 26px; }
  .calendar { padding: 16px; }
  .calendar-day { min-height: 43px; }
  .composer { padding: 12px; gap: 8px; }
  .composer > button { font-size: 11px; padding: 12px; }
}
`
