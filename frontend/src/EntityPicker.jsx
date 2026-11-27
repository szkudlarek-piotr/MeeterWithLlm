import { useEffect, useId, useRef, useState } from 'react'
import { api } from './api.js'
import PersonAvatar from './PersonAvatar.jsx'
import { t, tf } from './i18n.js'

export default function EntityPicker({ label, source, value, onChange, multiple = false, required = false, excludeIds = [], interactionType }) {
  const id = useId()
  const inputRef = useRef(null)
  const [query, setQuery] = useState('')
  const [options, setOptions] = useState([])
  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const display = item => item.fullName || item.name
  const peoplePicker = source.startsWith('people') || source === 'quotes/authors' || source === 'admin/users/people'
  const available = options.filter(item => !value.some(selected => selected.id === item.id) && !excludeIds.includes(item.id))

  useEffect(() => {
    if (!open) return
    const controller = new AbortController()
    setLoading(true); setError(''); setOptions([]); setActive(-1)
    api(`/${source}?q=${encodeURIComponent(query)}&limit=100${interactionType ? `&type=${encodeURIComponent(interactionType)}` : ''}`, { signal: controller.signal })
      .then(setOptions)
      .catch(error => { if (!controller.signal.aborted) setError(error.message) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [query, source, open, interactionType])

  function select(item) {
    onChange(multiple ? [...value, item] : [item])
    setQuery(''); setActive(-1); setOpen(false)
    if (!multiple) inputRef.current?.blur()
  }

  return <div className="entity-picker" onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false) }}>
    <label htmlFor={id}>{label}{required ? ' *' : ''}</label>
    <input ref={inputRef} id={id} role="combobox" aria-expanded={open} aria-controls={`${id}-options`} aria-autocomplete="list" aria-activedescendant={open && available[active] ? `${id}-${available[active].id}` : undefined}
      value={!multiple && !open && value.length ? display(value[0]) : query} placeholder={!multiple && value.length ? tf('Zmień wybór: {name}', { name: display(value[0]) }) : t('Wpisz, aby wyszukać…')} autoComplete="off" onFocus={() => setOpen(true)} onClick={() => setOpen(true)} onChange={event => { setQuery(event.target.value); setActive(-1); setOpen(true) }}
      onKeyDown={event => {
        if (event.key === 'ArrowDown') { event.preventDefault(); setOpen(true); setActive(index => Math.min(index + 1, available.length - 1)) }
        if (event.key === 'ArrowUp') { event.preventDefault(); setActive(index => Math.max(0, index - 1)) }
        if (event.key === 'Enter' && open) { event.preventDefault(); if (available[active]) select(available[active]) }
        if (event.key === 'Escape' && open) { event.preventDefault(); event.stopPropagation(); setOpen(false) }
      }} />
    {open && <div className="entity-options" role="listbox" id={`${id}-options`} aria-label={label} aria-busy={loading}>
      {available.map((item, index) => <button type="button" tabIndex={-1} role="option" aria-label={display(item)} aria-selected={active === index} id={`${id}-${item.id}`} key={item.id} className={`${active === index ? 'highlighted' : ''} ${peoplePicker ? 'person-option' : ''}`} onMouseDown={event => event.preventDefault()} onClick={() => select(item)}>{peoplePicker && <PersonAvatar person={item} />}<span>{display(item)}</span></button>)}
      {!available.length && <p role="status">{loading ? t('Wyszukiwanie…') : error || (peoplePicker ? t('Nie znaleziono pasujących osób dostępnych do wyboru.') : t('Nie znaleziono pasujących wyników.'))}</p>}
    </div>}
    {!multiple && value.length > 0 && <div className={peoplePicker ? 'selected-person' : undefined}>{peoplePicker && <><PersonAvatar person={value[0]} /><span>{display(value[0])}</span></>}<button className="text-button" type="button" aria-label={tf('Usuń wybór: {name}', { name: display(value[0]) })} onClick={() => { onChange([]); setQuery(''); setOpen(false) }}>{t('Usuń wybór')}</button></div>}
    {multiple && value.length > 0 && <div className="selected-entities">{value.map(item => <span key={item.id}>{peoplePicker && <PersonAvatar person={item} />}{display(item)}<button type="button" aria-label={tf('Usuń wybór: {name}', { name: display(item) })} onClick={() => onChange(value.filter(selected => selected.id !== item.id))}>×</button></span>)}</div>}
  </div>
}
