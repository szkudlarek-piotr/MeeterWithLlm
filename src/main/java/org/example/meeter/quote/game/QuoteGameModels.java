package org.example.meeter.quote.game;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.example.meeter.Human;

public final class QuoteGameModels {
    private QuoteGameModels() {}
    public record CreateInput(@Positive Long visitId, @Size(max = 10) Set<@NotNull @Positive Long> cliqueIds,
                              @Min(10) @Max(120) Integer secondsPerQuestion) {}
    public record GuessInput(@NotNull @Positive Long roundId, @Positive Long authorId) {}
    public record Person(Long id, String fullName) { static Person of(Human h) { return h == null ? null : new Person(h.getId(), h.getFullName()); } }
    public record Question(Long roundId, int position, String quote, Instant deadlineAt) {}
    public record Answer(Long roundId, int position, String quote, Person guessedAuthor, Person correctAuthor,
                         boolean correct, boolean timedOut, boolean selfQuote, Instant answeredAt) {}
    public record Summary(Long id, String scopeLabel, String scopeType, Instant createdAt, Instant finishedAt,
                          int total, int answered, int eligible, int correct, Double percentage, long elapsedMillis) {}
    public record View(Summary summary, List<Person> candidates, Question question, List<Answer> answers,
                       int secondsPerQuestion, Instant serverNow) {}
    public record History(List<Summary> content, int page, boolean hasNext) {}
    public record Score(int rank, Long gameId, Long playerId, String playerName, String scopeLabel,
                        int correct, int eligible, double percentage, long elapsedMillis, Instant finishedAt) {}
}
