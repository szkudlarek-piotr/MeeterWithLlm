import { GameProgress, GameTableScroll } from './styles/QuoteGamePanel.js'
import { useEffect, useRef, useState } from 'react'
import { api } from './api.js'
import EntityPicker from './EntityPicker.jsx'
import QuoteContent from './QuoteContent.jsx'

function VisitSelector({ value, onChange }) {
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  useEffect(() => {
    const controller = new AbortController()
    setLoading(true); setError(''); setResult(null)
    const timer = setTimeout(() => {
      api(`/quote-games/visits?q=${encodeURIComponent(query)}&page=${page}&size=10`, { signal: controller.signal })
        .then(setResult).catch(error => { if (!controller.signal.aborted) setError(error.message) })
        .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    }, 200)
    return () => { clearTimeout(timer); controller.abort() }
  }, [query, page])
  return <section aria-label="Wybór wizyty">
    <h4>Wybierz wizytę z listy</h4><label htmlFor="game-visit-search">Filtruj listę (opcjonalnie)</label><input id="game-visit-search" value={query} maxLength={200} onChange={event => { setQuery(event.target.value); setPage(0) }} placeholder="Nazwa wizyty…" />
    {value && <p className="save-notice">Wybrana wizyta: {value.name}</p>}
    <div className="game-visit-options">{result?.content.map(visit => <button type="button" key={visit.id} aria-pressed={value?.id === visit.id} onClick={() => onChange(visit)}><strong>{visit.name}</strong><small>{visit.startDate.slice(0, 10)} · {visit.participantCount} osób</small></button>)}</div>
    {loading && <p role="status">Wczytywanie wizyt…</p>}{error && <p className="field-error" role="alert">{error}</p>}
    {!loading && !error && result?.content.length === 0 && <p className="field-hint">Brak wizyt. Dodaj wizytę z uczestnikami albo wybierz kliki.</p>}
    {(page > 0 || result?.hasNext) && <div className="event-photo-pages"><button type="button" disabled={loading || !page} onClick={() => setPage(value => value - 1)}>Poprzednie wizyty</button><span>{page + 1}</span><button type="button" disabled={loading || !result?.hasNext} onClick={() => setPage(value => value + 1)}>Następne wizyty</button></div>}
  </section>
}

const percent = value => value == null ? 'Poza rankingiem' : `${value.toLocaleString('pl-PL', { maximumFractionDigits: 1 })}%`

export default function QuoteGamePanel({ single = false }) {
  const [mode, setMode] = useState('visit')
  const [visit, setVisit] = useState(null)
  const [cliques, setCliques] = useState([])
  const [seconds, setSeconds] = useState(30)
  const [game, setGame] = useState(null)
  const [choice, setChoice] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [remaining, setRemaining] = useState(null)
  const [history, setHistory] = useState(null)
  const [historyPage, setHistoryPage] = useState(0)
  const [scores, setScores] = useState([])
  const [listError, setListError] = useState('')
  const [listsLoading, setListsLoading] = useState(false)
  const [reload, setReload] = useState(0)
  const offset = useRef(0)
  const sending = useRef(false)
  const timedOutRound = useRef(null)

  useEffect(() => {
    const controller = new AbortController()
    setListsLoading(true); setListError('')
    const historyPath = single ? `/quote-games/single/history?page=${historyPage}` : `/quote-games?page=${historyPage}`
    const leaderboardPath = single ? '/quote-games/single/leaderboard' : '/quote-games/leaderboard'
    Promise.all([api(historyPath, { signal: controller.signal }), api(leaderboardPath, { signal: controller.signal })])
      .then(([history, scores]) => { setHistory(history); setScores(scores) })
      .catch(error => { if (!controller.signal.aborted) setListError(error.message) })
      .finally(() => { if (!controller.signal.aborted) setListsLoading(false) })
    return () => controller.abort()
  }, [reload, historyPage, single])

  function receive(next) {
    offset.current = Date.parse(next.serverNow) - Date.now()
    setGame(next); setChoice(null)
    setRemaining(next.question ? Math.max(0, Math.ceil((Date.parse(next.question.deadlineAt) - Date.now() - offset.current) / 1000)) : null)
    setReload(value => value + 1)
  }
  async function run(action) {
    if (sending.current) return
    sending.current = true; setBusy(true); setError('')
    try { receive(await action()) }
    catch (error) { setError(error.message) }
    finally { sending.current = false; setBusy(false) }
  }
  async function answer(authorId, roundId) {
    await run(() => api(`/quote-games/${game.summary.id}/guesses`, { body: { roundId, authorId } }))
  }
  useEffect(() => {
    if (!game?.question) return
    const deadline = Date.parse(game.question.deadlineAt)
    const tick = () => setRemaining(Math.max(0, Math.ceil((deadline - Date.now() - offset.current) / 1000)))
    tick(); const timer = setInterval(tick, 200)
    return () => clearInterval(timer)
  }, [game?.question?.roundId, game?.question?.deadlineAt])
  useEffect(() => {
    const roundId = game?.question?.roundId
    if (remaining === 0 && roundId && !busy && timedOutRound.current !== roundId) {
      timedOutRound.current = roundId
      answer(null, roundId)
    }
  }, [remaining, game?.question?.roundId, busy])

  function create(event) {
    event.preventDefault()
    if (single) {
      run(() => api('/quote-games/single', { body: { secondsPerQuestion: Number(seconds) } }))
      return
    }
    if (mode === 'visit' && !visit || mode === 'cliques' && !cliques.length) { setError('Wybierz wizytę albo co najmniej jedną klikę.'); return }
    run(() => api('/quote-games', { body: { visitId: mode === 'visit' ? visit.id : null, cliqueIds: mode === 'cliques' ? cliques.map(item => item.id) : [], secondsPerQuestion: Number(seconds) } }))
  }
  const last = game?.answers.at(-1)
  return <section className="quote-game" aria-label={single ? 'KTO ETA SKAZAŁ' : 'Zgadnij autora cytatu'}>
    {error && <p className="field-error" role="alert">{error}{game && <button className="text-button" disabled={busy} onClick={() => run(() => api(`/quote-games/${game.summary.id}`))}>Odśwież stan gry</button>}</p>}
    {!game ? <form className="card interaction-form game-setup" onSubmit={create}>
      <h3>{single ? 'KTO ETA SKAZAŁ' : 'Kto to powiedział?'}</h3><p className="form-intro">{single ? 'Jedna gra, jeden losowy autor i jeden jego publiczny cytat. Zgadnij autora spośród wszystkich ludzi.' : 'Wybierz towarzystwo. Wylosujemy maksymalnie dwa publiczne cytaty każdej osoby, a potem przetasujemy całą pulę.'}</p>
      <fieldset disabled={busy}>
        {!single && <><label htmlFor="game-mode">Pula cytatów</label><select id="game-mode" value={mode} onChange={event => setMode(event.target.value)}><option value="visit">Uczestnicy wizyty</option><option value="cliques">Członkowie wybranych klik</option></select>
          {mode === 'visit' ? <VisitSelector value={visit} onChange={setVisit} /> : <EntityPicker label="Wybierz kliki (maksymalnie 10)" source="cliques" multiple value={cliques} onChange={setCliques} />}</>}
        <label htmlFor="game-seconds">Sekundy na cytat</label><input id="game-seconds" type="number" required min={10} max={120} value={seconds} onChange={event => setSeconds(event.target.value)} />
        <p className="field-hint">Własne cytaty nie liczą się do wyniku. Upływ czasu oznacza nietrafioną odpowiedź. Każdy gracz ma własną rozgrywkę.</p>
        <button className="primary submit" disabled={busy}>{busy ? 'Losowanie…' : 'Zaczynamy →'}</button>
      </fieldset>
    </form> : <div className="card game-play">
      <GameProgress><span>{game.summary.scopeLabel}</span><strong>{game.summary.answered}/{game.summary.total}</strong></GameProgress>
      <progress value={game.summary.answered} max={game.summary.total} aria-label="Postęp rozgrywki" />
      {game.question ? <>
        <div className={`game-clock ${remaining <= 5 ? 'urgent' : ''}`} role="timer" aria-label="Pozostały czas">◷ {remaining} s</div>
        <p className="eyebrow">CYTAT {game.question.position} Z {game.summary.total}</p><QuoteContent quote={game.question.quote} />
        <form onSubmit={event => { event.preventDefault(); if (choice) answer(choice, game.question.roundId) }}><fieldset disabled={busy || remaining === 0}><legend>Kto jest autorem?</legend><div className="game-candidates">{game.candidates.map(person => <label key={person.id} className={choice === person.id ? 'chosen' : ''}><input type="radio" name="game-author" value={person.id} checked={choice === person.id} onChange={() => setChoice(person.id)} required />{person.fullName}</label>)}</div><button className="primary submit" disabled={!choice || busy || remaining === 0}>{busy ? 'Zapisywanie…' : 'Zatwierdź odpowiedź'}</button></fieldset></form>
      </> : <>
        {last && <div className="game-feedback" role="status"><h3>{last.timedOut ? 'Czas minął' : last.correct ? 'Trafiony autor!' : 'Tym razem nie'}</h3><QuoteContent quote={last.quote} /><p>Autor: <strong>{last.correctAuthor.fullName}</strong></p><p>Twój typ: {last.guessedAuthor?.fullName || 'Brak odpowiedzi'}</p>{last.selfQuote && <p className="field-hint">To Twój cytat — nie wpływa na wynik w rankingu.</p>}</div>}
        {game.summary.finishedAt ? <div className="game-final"><h3>Koniec rozgrywki!</h3><strong>{percent(game.summary.percentage)}</strong><p>{game.summary.correct} trafień na {game.summary.eligible} cytatów innych osób.</p>{!game.summary.eligible && <p>W tej grze były tylko Twoje cytaty, więc nie trafia do rankingu.</p>}<button className="primary" onClick={() => { setGame(null); setError('') }}>Nowa gra</button></div> : <button className="primary" disabled={busy} onClick={() => run(() => api(`/quote-games/${game.summary.id}/next`, { body: {} }))}>{busy ? 'Wczytywanie…' : 'Następny cytat →'}</button>}
      </>}
      <p className="field-hint">Wynik jest liczony wyłącznie z cytatów innych osób. Odpowiedzi zapisują się w bazie.</p>
      <button className="text-button" disabled={busy} onClick={() => { setGame(null); setError(''); setReload(value => value + 1) }}>{game.question ? 'Wróć do wyboru gry (czas nadal biegnie)' : 'Wróć do wyboru gry'}</button>
    </div>}

    <section className="card game-leaderboard" aria-label="Tabela wyników"><h3>Tabela wyników</h3><p className="field-hint">Najlepsza ukończona gra każdego gracza. Ranking według procentu trafień w nieswoich cytatach; przy remisie wygrywa krótszy czas tych odpowiedzi.</p>
      {listError && <p className="field-error" role="alert">{listError} <button className="text-button" onClick={() => setReload(value => value + 1)}>Spróbuj ponownie</button></p>}
      {listsLoading && <p role="status">Wczytywanie wyników…</p>}
      {!scores.length && !listsLoading && !listError && <p>Jeszcze brak wyników. Pierwsza ukończona gra może być Twoja.</p>}
      {!!scores.length && <GameTableScroll><table><thead><tr><th>Miejsce</th><th>Gracz</th><th>Skuteczność</th><th>Trafienia</th><th>Czas</th></tr></thead><tbody>{scores.map(score => <tr key={score.playerId}><td>{score.rank}</td><td>{score.playerName}</td><td><strong>{percent(score.percentage)}</strong></td><td>{score.correct}/{score.eligible}</td><td>{(score.elapsedMillis / 1000).toLocaleString('pl-PL', { maximumFractionDigits: 1 })} s</td></tr>)}</tbody></table></GameTableScroll>}
    </section>
    <section className="card game-history" aria-label="Twoje rozgrywki"><h3>Twoje rozgrywki</h3>{history?.content.map(item => <div className="game-history-row" key={item.id}><div><strong>{item.scopeLabel}</strong><small>{new Date(item.createdAt).toLocaleString('pl-PL')} · {item.answered}/{item.total} odpowiedzi{item.finishedAt ? ` · ${percent(item.percentage)}` : ' · w toku'}</small></div><button className="text-button" disabled={busy} onClick={() => run(() => api(`/quote-games/${item.id}`))}>{item.finishedAt ? 'Wynik' : 'Wznów'}</button></div>)}{history?.content.length === 0 && <p>Tu znajdziesz zapisane gry i wrócisz do rozpoczętej rozgrywki.</p>}
      {(historyPage > 0 || history?.hasNext) && <div className="event-photo-pages"><button disabled={!historyPage || listsLoading} onClick={() => setHistoryPage(value => value - 1)}>Nowsze gry</button><span>{historyPage + 1}</span><button disabled={!history?.hasNext || listsLoading} onClick={() => setHistoryPage(value => value + 1)}>Starsze gry</button></div>}
    </section>
  </section>
}
