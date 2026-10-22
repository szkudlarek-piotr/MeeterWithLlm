package org.example.meeter.quote.game;

import static org.example.meeter.quote.game.QuoteGameModels.*;
import java.time.Instant;
import java.util.*;
import org.example.meeter.Human;
import org.example.meeter.auth.*;
import org.example.meeter.quote.QuoteRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional
public class QuoteGameService {
    private final QuoteGameRepository games;
    private final QuoteGamePool pool;
    private final QuoteRepository quotes;
    private final CurrentAccount account;
    private final UserRepository users;
    public QuoteGameService(QuoteGameRepository games, QuoteGamePool pool, QuoteRepository quotes, CurrentAccount account, UserRepository users) {
        this.games = games; this.pool = pool; this.quotes = quotes; this.account = account; this.users = users;
    }
    public View create(CreateInput input) {
        var selection = pool.draw(input.visitId(), input.cliqueIds());
        int seconds = input.secondsPerQuestion() == null ? 30 : input.secondsPerQuestion();
        QuoteGame game = new QuoteGame(users.getReferenceById(account.id()), selection.visit(), selection.cliques(), selection.people(), selection.quotes(), seconds);
        game.getRounds().getFirst().start(seconds);
        return view(games.saveAndFlush(game));
    }
    private QuoteGame own(Long id) {
        return games.ownedForUpdate(id, account.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono rozgrywki."));
    }
    private GameRound current(QuoteGame game) { return game.getRounds().stream().filter(round -> round.getGuess() == null).findFirst().orElse(null); }
    private void ensurePublic(QuoteGame game) {
        var ids = game.getRounds().stream().map(round -> round.getQuote().getId()).toList();
        long visible = quotes.count((root, query, cb) -> cb.and(root.get("id").in(ids), cb.isTrue(root.get("isPublic"))));
        if (visible != ids.size()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cytat nie jest już publiczny. Rozpocznij nową grę.");
    }
    private void expire(QuoteGame game) {
        GameRound round = current(game);
        if (round != null && round.getDeadlineAt() != null && !Instant.now().isBefore(round.getDeadlineAt())) {
            round.answer(null, users.getReferenceById(account.id()));
            if (current(game) == null) game.finish();
            games.flush();
        }
    }
    public View get(Long id) {
        QuoteGame game = own(id); ensurePublic(game); expire(game); return view(game);
    }
    public View next(Long id) {
        QuoteGame game = own(id); ensurePublic(game); expire(game);
        GameRound round = current(game);
        if (round != null) round.start(game.getSecondsPerQuestion());
        games.flush(); return view(game);
    }
    public View guess(Long id, GuessInput input) {
        QuoteGame game = own(id); ensurePublic(game);
        GameRound round = current(game);
        if (round == null || !round.getId().equals(input.roundId())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ta runda nie jest bieżącym pytaniem lub odpowiedź jest już zapisana.");
        if (round.getStartedAt() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Najpierw rozpocznij kolejne pytanie.");
        Human chosen = null;
        if (input.authorId() != null) chosen = game.getCandidates().stream().map(GameCandidate::getHuman)
            .filter(person -> person.getId().equals(input.authorId())).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wybierz osobę z tej rozgrywki."));
        if (chosen == null && Instant.now().isBefore(round.getDeadlineAt()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wybierz autora cytatu.");
        round.answer(chosen, users.getReferenceById(account.id()));
        if (current(game) == null) game.finish();
        games.flush(); return view(game);
    }
    @Transactional(readOnly = true)
    public History history(int page) {
        var result = games.findByCreatedById(account.id(), PageRequest.of(page, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new History(result.stream().map(this::summary).toList(), page, result.hasNext());
    }
    private Summary summary(QuoteGame game) {
        int answered = 0, eligible = 0, correct = 0; long elapsed = 0;
        for (GameRound round : game.getRounds()) {
            boolean self = round.getCorrectAuthor().getId().equals(game.getPlayerHuman().getId());
            if (!self) eligible++;
            if (round.getGuess() != null) {
                answered++; elapsed += round.getGuess().getElapsedMillis();
                if (!self && round.getGuess().isCorrect()) correct++;
            }
        }
        return new Summary(game.getId(), game.getScopeLabel(), game.getScopeType(), game.getCreatedAt(), game.getFinishedAt(),
            game.getRounds().size(), answered, eligible, correct, eligible == 0 ? null : 100.0 * correct / eligible, elapsed);
    }
    private View view(QuoteGame game) {
        GameRound round = current(game);
        Question question = round == null || round.getStartedAt() == null ? null : new Question(round.getId(), round.getPosition(), round.getQuoteText(), round.getDeadlineAt());
        var answers = game.getRounds().stream().filter(item -> item.getGuess() != null).map(item -> {
            QuoteGuess guess = item.getGuess();
            return new Answer(item.getId(), item.getPosition(), item.getQuoteText(), Person.of(guess.getGuessedAuthor()),
                Person.of(item.getCorrectAuthor()), guess.isCorrect(), guess.isTimedOut(), guess.isSelfQuote(), guess.getCreatedAt());
        }).toList();
        return new View(summary(game), game.getCandidates().stream().map(candidate -> Person.of(candidate.getHuman()))
            .sorted(Comparator.comparing(Person::fullName).thenComparing(Person::id)).toList(), question, answers, game.getSecondsPerQuestion(), Instant.now());
    }
    @Transactional(readOnly = true)
    public List<Score> leaderboard() {
        var sorted = new ArrayList<>(games.scores());
        sorted.removeIf(row -> row.getEligible() == 0);
        sorted.sort(Comparator.<QuoteGameRepository.ScoreRow>comparingDouble(row -> (double) row.getCorrect() / row.getEligible()).reversed()
            .thenComparingLong(QuoteGameRepository.ScoreRow::getElapsedMillis).thenComparing(QuoteGameRepository.ScoreRow::getFinishedAt).thenComparing(QuoteGameRepository.ScoreRow::getGameId));
        Set<Long> players = new HashSet<>(); List<Score> result = new ArrayList<>();
        for (var row : sorted) {
            if (!players.add(row.getPlayerId())) continue;
            result.add(new Score(result.size() + 1, row.getGameId(), row.getPlayerId(), (row.getFirstName() + " " + (row.getSurname() == null ? "" : row.getSurname())).trim(),
                row.getScopeLabel().equals("VISIT") ? "Wizyta" : "Kliki", row.getCorrect().intValue(), row.getEligible().intValue(),
                100.0 * row.getCorrect() / row.getEligible(), row.getElapsedMillis(), row.getFinishedAt()));
            if (result.size() == 100) break;
        }
        return result;
    }
}
