import { t } from './i18n.js'

let csrfRequest
async function csrf() {
  if (!csrfRequest) csrfRequest = fetch('/api/v2/auth/csrf', { credentials: 'same-origin' })
    .then(async response => {
      if (!response.ok) throw new Error(response.status >= 500
        ? t('Backend jest niedostępny. Sprawdź, czy działa na porcie 8091, i spróbuj ponownie.')
        : t('Nie udało się przygotować logowania. Odśwież stronę i spróbuj ponownie.'))
      return response.json()
    }).catch(error => { csrfRequest = undefined; throw error })
  return csrfRequest
}

export async function api(path, { body, signal, method = body === undefined ? 'GET' : 'POST' } = {}) {
  const headers = body === undefined ? {} : { 'Content-Type': 'application/json' }
  if (!['GET', 'HEAD'].includes(method)) {
    const token = await csrf()
    headers[token.headerName] = token.token
  }
  const response = await fetch(`/api/v2${path}`, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
    signal,
  })
  if (!response.ok) {
    if (response.status === 403) csrfRequest = undefined
    if (response.status === 401 && !path.startsWith('/auth/')) window.dispatchEvent(new Event('meeter:unauthorized'))
    const problem = await response.json().catch(() => ({}))
    const error = new Error(problem.detail || t('Nie udało się połączyć z backendem. Sprawdź, czy działa na porcie 8091.'))
    error.status = response.status
    throw error
  }
  if (response.status === 204) return null
  return response.json()
}

