import { useEffect, useState } from 'react'
import { api } from './api.js'
import EntityPicker from './EntityPicker.jsx'
import PersonAvatar from './PersonAvatar.jsx'
import { t } from './i18n.js'

const generationTitle = generation => generation === 0 ? t('Ta osoba, rodzeństwo i małżonkowie')
  : generation === -1 ? t('Rodzice') : generation === -2 ? t('Dziadkowie') : generation === -3 ? t('Pradziadkowie')
  : generation < 0 ? t('Dalsi przodkowie') : generation === 1 ? t('Dzieci') : generation === 2 ? t('Wnuki') : t('Dalsi potomkowie')

export default function FamilyPanel({ person }) {
  const [tree, setTree] = useState(null)
  const [error, setError] = useState('')
  const [reload, setReload] = useState(0)
  const [relation, setRelation] = useState('PARENT_OF_THIS')
  const [other, setOther] = useState([])
  const [busy, setBusy] = useState(false)
  const [formError, setFormError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    setError('')
    api(`/people/browse/${person.id}/family`, { signal: controller.signal })
      .then(setTree).catch(failure => { if (!controller.signal.aborted) setError(failure.message) })
    return () => controller.abort()
  }, [person.id, reload])

  async function add(event) {
    event.preventDefault()
    if (!other.length) { setFormError(t('Wybierz osobę.')); return }
    const otherId = other[0].id
    const body = relation === 'PARENT_OF_THIS' ? { type: 'PARENT', personAId: otherId, personBId: person.id }
      : relation === 'CHILD_OF_THIS' ? { type: 'PARENT', personAId: person.id, personBId: otherId }
      : { type: 'SPOUSE', personAId: person.id, personBId: otherId }
    setBusy(true); setFormError('')
    try { await api('/family/links', { body }); setOther([]); setReload(value => value + 1) }
    catch (failure) { setFormError(failure.message) }
    finally { setBusy(false) }
  }

  async function remove(link) {
    if (!window.confirm(t('Usunąć tę relację?'))) return
    setError('')
    try { await api(`/family/links/${link.id}`, { method: 'DELETE' }); setReload(value => value + 1) }
    catch (failure) { setError(failure.message) }
  }

  const names = new Map((tree?.people ?? []).map(node => [node.person.id, node.person.fullName]))
  const rows = [...new Set((tree?.people ?? []).map(node => node.generation))].sort((a, b) => a - b)
  return <section className="family-panel">
    {error && <p className="field-error" role="alert">{error}</p>}
    {!tree && !error && <p role="status">{t('Wczytywanie…')}</p>}
    {tree && !tree.links.length && <p className="field-hint">{t('Brak zapisanych relacji rodzinnych.')}</p>}
    {tree && tree.links.length > 0 && <div className="family-tree">
      {rows.map(generation => <div className="family-generation" key={generation}>
        <h4>{generationTitle(generation)}</h4>
        <ul>{tree.people.filter(node => node.generation === generation).map(node =>
          <li key={node.person.id} className={node.person.id === person.id ? 'is-focus' : undefined}><PersonAvatar person={node.person} /><span>{node.person.fullName}</span></li>)}
        </ul>
      </div>)}
      <h4>{t('Relacje')}</h4>
      <ul className="family-links">{tree.links.map(link => <li key={link.id}>
        <span>{link.type === 'PARENT'
          ? `${t('Rodzic')}: ${names.get(link.personAId)} → ${t('dziecko')}: ${names.get(link.personBId)}`
          : `${t('Małżonkowie')}: ${names.get(link.personAId)} + ${names.get(link.personBId)}`}</span>
        <button type="button" className="text-button" onClick={() => remove(link)}>{t('Usuń')}</button>
      </li>)}</ul>
    </div>}
    <form className="family-add" onSubmit={add}>
      <h4>{t('Dodaj relację')}</h4>
      <fieldset disabled={busy}>
        <label htmlFor="family-relation">{t('Relacja')}</label>
        <select id="family-relation" value={relation} onChange={event => setRelation(event.target.value)}>
          <option value="PARENT_OF_THIS">{t('Ta osoba ma rodzica')}</option>
          <option value="CHILD_OF_THIS">{t('Ta osoba ma dziecko')}</option>
          <option value="SPOUSE">{t('Ta osoba ma małżonka')}</option>
        </select>
        <EntityPicker label={t('Osoba')} source="people" value={other} onChange={setOther} excludeIds={[person.id]} />
        {formError && <p className="field-error" role="alert">{formError}</p>}
        <button className="primary" disabled={busy}>{busy ? t('Zapisywanie…') : t('Dodaj')}</button>
      </fieldset>
    </form>
  </section>
}
