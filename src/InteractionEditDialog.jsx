import { useEffect, useRef, useState } from 'react'
import { api } from './api.js'
import InteractionForm, { interactionEntry, interactionTypes } from './InteractionForm.jsx'

export default function InteractionEditDialog({ interactionId, actorId, onClose, onSaved }) {
  const dialog = useRef(null)
  const [interaction, setInteraction] = useState(null)
  const [error, setError] = useState('')
  const [retry, setRetry] = useState(0)
  const [saving, setSaving] = useState(false)
  useEffect(() => {
    dialog.current.showModal()
    const controller = new AbortController()
    setError(''); setInteraction(null)
    api(`/interactions/${interactionId}`, { signal: controller.signal })
      .then(data => { if (!controller.signal.aborted) setInteraction(data) })
      .catch(failure => { if (!controller.signal.aborted) setError(failure.message) })
    return () => controller.abort()
  }, [interactionId, retry])

  async function save(endpoint, body) {
    setSaving(true)
    try {
      const saved = await api(`${endpoint}/${interactionId}`, { method: 'PUT', body })
      onSaved(interactionEntry(saved))
      onClose()
    } finally { setSaving(false) }
  }
  const category = interaction && Object.keys(interactionTypes).find(key => interactionTypes[key].type === interaction.type)
  return <dialog ref={dialog} className="login-dialog" aria-labelledby="edit-interaction-title" onCancel={event => { event.preventDefault(); if (!saving) onClose() }}>
    <button type="button" className="close-dialog" aria-label="Zamknij edycję" disabled={saving} onClick={onClose}>×</button>
    <p className="eyebrow">TWOJA HISTORIA</p>
    <h2 id="edit-interaction-title">Edytuj interakcję</h2>
    {error ? <p className="field-error" role="alert">{error} <button type="button" onClick={() => setRetry(value => value + 1)}>Spróbuj ponownie</button></p>
      : interaction ? <InteractionForm key={interaction.id} category={category} actorId={actorId} initialInteraction={interaction} onSave={save} />
        : <p role="status">Wczytywanie interakcji…</p>}
    <button type="button" className="text-button" disabled={saving} onClick={onClose}>Anuluj</button>
  </dialog>
}
