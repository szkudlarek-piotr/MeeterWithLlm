import { useEffect, useState } from 'react'

export default function PersonAvatar({ person }) {
  const [failed, setFailed] = useState(false)
  const photo = person.profilePhotoUrl || person.photoUrl
  const name = person.fullName || person.name || ''
  useEffect(() => setFailed(false), [photo])
  const initials = name.split(' ').filter(Boolean).map(part => part[0]).slice(0, 2).join('')
  return <span className="calendar-avatar" title={name}>
    {photo && !failed ? <img src={photo} alt="" loading="lazy" onError={() => setFailed(true)} /> : <span aria-label={name}>{initials}</span>}
  </span>
}
