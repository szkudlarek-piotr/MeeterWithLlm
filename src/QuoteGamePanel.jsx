import { GameProgress, GameTableScroll } from './styles/QuoteGamePanel.js'
import { useEffect, useRef, useState } from 'react'
import { api } from './api.js'
import EntityPicker from './EntityPicker.jsx'
import QuoteContent from './QuoteContent.jsx'
import { t, tf, dateLocale } from './i18n.js'

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
  return <section aria-label={t('Wybór wizyty')}>
    <h4>{t('Wybierz wizytę z listy')}</h4><label htmlFor="game-visit-search">{t('Filtruj listę (opcjonalnie)')}</label><input id="game-visit-search" value={query} maxLength={200} onChange={event => { setQuery(event.target.value); setPage(0) }} placeholder={t('Nazwa wizyty…')} />
    {value && <p className="save-notice">{tf('Wybrana wizyta: {name}', { name: value.name })}</p>}
    <div className="game-visit-options">{result?.content.map(visit => <button type="button" key={visit.id} aria-pressed={value?.id === visit.id} onClick={() => onChange(visit)}><strong>{visit.name}</strong><small>{visit.startDate.slice(0, 10)} · {visit.participantCount} {t('osób')}</small></button>)}</div>
    {loading && <p role="status">{t('Wczytywanie wizyt…')}</p>}{error && <p className="field-error" role="alert">{error}</p>}
    {!loading && !error && result?.content.length === 0 && <p className="field-hint">{t('Brak wizyt. Dodaj wizytę z uczestnikami albo wybierz kliki.')}</p>}
    {(page > 0 || result?.hasNext) && <div className="event-photo-pages"><button type="button" disabled={loading || !page} onClick={() => setPage(value => value - 1)}>{t('Poprzednie wizyty')}</button><span>{page + 1}</span><button type="button" disabled={loading || !result?.hasNext} onClick={() => setPage(value => value + 1)}>{t('Następne wizyty')}</button></div>}
  </section>
}

const percent = value => value == null ? t('Poza rankingiem') : `${value.toLocaleString(dateLocale(), { maximumFractionDigits: 1 })}%`

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
    if (mode === 'visit' && !visit || mode === 'cliques' && !cliques.length) { setError(t('Wybierz wizytę albo co najmniej jedną klikę.')); return }
    run(() => api('/quote-games', { body: { visitId: mode === 'visit' ? visit.id : null, cliqueIds: mode === 'cliques' ? cliques.map(item => item.id) : [], secondsPerQuestion: Number(seconds) } }))
  }
  const last = game?.answers.at(-1)
  return <section className="quote-game" aria-label={single ? t('KTO ETA SKAZAŁ') : t('Zgadnij autora cytatu')}>
    {error && <p className="field-error" role="alert">{error}{game && <button className="text-button" disabled={busy} onClick={() => run(() => api(`/quote-games/${game.summary.id}`))}>{t('Odśwież stan gry')}</button>}</p>}
    {!game ? <form className="card interaction-form game-setup" onSubmit={create}>
      <h3>{single ? t('KTO ETA SKAZAŁ') : t('Kto to powiedział?')}</h3><p className="form-intro">{single ? t('Jedna gra, jeden losowy autor i jeden jego publiczny cytat. Zgadnij autora spośród wszystkich ludzi.') : t('Wybierz towarzystwo. Wylosujemy maksymalnie dwa publiczne cytaty każdej osoby, a potem przetasujemy całą pulę.')}</p>
      <fieldset disabled={busy}>
        {!single && <><label htmlFor="game-mode">{t('Pula cytatów')}</label><select id="game-mode" value={mode} onChange={event => setMode(event.target.value)}><option value="visit">{t('Uczestnicy wizyty')}</option><option value="cliques">{t('Członkowie wybranych klik')}</option></select>
          {mode === 'visit' ? <VisitSelector value={visit} onChange={setVisit} /> : <EntityPicker label={t('Wybierz kliki (maksymalnie 10)')} source="cliques" multiple value={cliques} onChange={setCliques} />}</>}
        <label htmlFor="game-seconds">{t('Sekundy na cytat')}</label><input id="game-seconds" type="number" required min={10} max={120} value={seconds} onChange={event => setSeconds(event.target.value)} />
        <p className="field-hint">{t('Własne cytaty nie liczą się do wyniku. Upływ czasu oznacza nietrafioną odpowiedź. Każdy gracz ma własną rozgrywkę.')}</p>
        <button className="primary submit" disabled={busy}>{busy ? t('Losowanie…') : t("Let's start →")}</button>
      </fieldset>
    </form> : <div className="card game-play">
      <GameProgress><span>{game.summary.scopeLabel}</span><strong>{game.summary.answered}/{game.summary.total}</strong></GameProgress>
      <progress value={game.summary.answered} max={game.summary.total} aria-label={t('Postęp rozgrywki')} />
      {game.question ? <>
        <div className={`game-clock ${remaining <= 5 ? 'urgent' : ''}`} role="timer" aria-label={t('Pozostały czas')}>◷ {remaining} s</div>
        <p className="eyebrow">{tf('CYTAT {position} Z {total}', { position: game.question.position, total: game.summary.total })}</p><QuoteContent quote={game.question.quote} />
        <form onSubmit={event => { event.preventDefault(); if (choice) answer(choice, game.question.roundId) }}><fieldset disabled={busy || remaining === 0}><legend>{t('Kto jest autorem?')}</legend><div className="game-candidates">{game.candidates.map(person => <label key={person.id} className={choice === person.id ? 'chosen' : ''}><input type="radio" name="game-author" value={person.id} checked={choice === person.id} onChange={() => setChoice(person.id)} required />{person.fullName}</label>)}</div><button className="primary submit" disabled={!choice || busy || remaining === 0}>{busy ? t('Zapisywanie…') : t('Zatwierdź odpowiedź')}</button></fieldset></form>
      </> : <>
        {last && <div className="game-feedback" role="status"><h3>{last.timedOut ? t('Czas minął') : last.correct ? t('Trafiony autor!') : t('Tym razem nie')}</h3><QuoteContent quote={last.quote} /><p>{t('Autor:')} <strong>{last.correctAuthor.fullName}</strong></p><p>{t('Twój typ:')} {last.guessedAuthor?.fullName || t('Brak odpowiedzi')}</p>{last.selfQuote && <p className="field-hint">{t('To Twój cytat — nie wpływa na wynik w rankingu.')}</p>}</div>}
        {game.summary.finishedAt ? <div className="game-final"><h3>{t('Koniec rozgrywki!')}</h3><strong>{percent(game.summary.percentage)}</strong><p>{tf('{correct} trafień na {eligible} cytatów innych osób.', { correct: game.summary.correct, eligible: game.summary.eligible })}</p>{!game.summary.eligible && <p>{t('W tej grze były tylko Twoje cytaty, więc nie trafia do rankingu.')}</p>}<button className="primary" onClick={() => { setGame(null); setError('') }}>{t('Nowa gra')}</button></div> : <button className="primary" disabled={busy} onClick={() => run(() => api(`/quote-games/${game.summary.id}/next`, { body: {} }))}>{busy ? t('Wczytywanie…') : t('Następny cytat →')}</button>}
      </>}
      <p className="field-hint">{t('Wynik jest liczony wyłącznie z cytatów innych osób. Odpowiedzi zapisują się w bazie.')}</p>
      <button className="text-button" disabled={busy} onClick={() => { setGame(null); setError(''); setReload(value => value + 1) }}>{game.question ? t('Wróć do wyboru gry (czas nadal biegnie)') : t('Wróć do wyboru gry')}</button>
    </div>}

    <section className="card game-leaderboard" aria-label={t('Tabela wyników')}><h3>{t('Tabela wyników')}</h3><p className="field-hint">{t('Najlepsza ukończona gra każdego gracza. Ranking według procentu trafień w nieswoich cytatach; przy remisie wygrywa krótszy czas tych odpowiedzi.')}</p>
      {listError && <p className="field-error" role="alert">{listError} <button className="text-button" onClick={() => setReload(value => value + 1)}>{t('Spróbuj ponownie')}</button></p>}
      {listsLoading && <p role="status">{t('Wczytywanie wyników…')}</p>}
      {!scores.length && !listsLoading && !listError && <p>{t('Jeszcze brak wyników. Pierwsza ukończona gra może być Twoja.')}</p>}
      {!!scores.length && <GameTableScroll><table><thead><tr><th>{t('Miejsce')}</th><th>{t('Gracz')}</th><th>{t('Skuteczność')}</th><th>{t('Trafienia')}</th><th>{t('Czas')}</th></tr></thead><tbody>{scores.map(score => <tr key={score.playerId}><td>{score.rank}</td><td>{score.playerName}</td><td><strong>{percent(score.percentage)}</strong></td><td>{score.correct}/{score.eligible}</td><td>{(score.elapsedMillis / 1000).toLocaleString(dateLocale(), { maximumFractionDigits: 1 })} s</td></tr>)}</tbody></table></GameTableScroll>}
    </section>
    <section className="card game-history" aria-label={t('Twoje rozgrywki')}><h3>{t('Twoje rozgrywki')}</h3>{history?.content.map(item => <div className="game-history-row" key={item.id}><div><strong>{item.scopeLabel}</strong><small>{new Date(item.createdAt).toLocaleString(dateLocale())} · {item.answered}/{item.total} {t('odpowiedzi')}{item.finishedAt ? ` · ${percent(item.percentage)}` : ` · ${t('w toku')}`}</small></div><button className="text-button" disabled={busy} onClick={() => run(() => api(`/quote-games/${item.id}`))}>{item.finishedAt ? t('Wynik') : t('Wznów')}</button></div>)}{history?.content.length === 0 && <p>{t('Tu znajdziesz zapisane gry i wrócisz do rozpoczętej rozgrywki.')}</p>}
      {(historyPage > 0 || history?.hasNext) && <div className="event-photo-pages"><button disabled={!historyPage || listsLoading} onClick={() => setHistoryPage(value => value - 1)}>{t('Nowsze gry')}</button><span>{historyPage + 1}</span><button disabled={!history?.hasNext || listsLoading} onClick={() => setHistoryPage(value => value + 1)}>{t('Starsze gry')}</button></div>}
    </section>
  </section>
}
