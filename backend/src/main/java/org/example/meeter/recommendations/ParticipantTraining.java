package org.example.meeter.recommendations;

import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Hourly catch-up for the online participant ranker: while Meeter runs, every account's network learns interactions
 * that are new or changed, so a suggestion request does not have to. Runs only when the model worker is available.
 */
@Component
public class ParticipantTraining {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ParticipantTraining.class);
    // One call trains for at most the worker's budget; a few rounds drain a long backlog without hogging the worker.
    private static final int ROUNDS_PER_ACCOUNT = 4;
    private final ParticipantModelClient models;
    private final ParticipantModelWorker worker;
    private final ParticipantHistory history;
    private final NamedParameterJdbcTemplate jdbc;

    public ParticipantTraining(ParticipantModelClient models, ParticipantModelWorker worker,
                               ParticipantHistory history, NamedParameterJdbcTemplate jdbc) {
        this.models = models; this.worker = worker; this.history = history; this.jdbc = jdbc;
    }

    @Scheduled(initialDelayString = "${app.recommendations.training-initial-delay:PT5M}",
               fixedDelayString = "${app.recommendations.training-interval:PT1H}")
    public void trainAllAccounts() {
        if (!worker.enabled()) return;
        var accounts = jdbc.query("select id, human_id from app_users order by id", Map.of(),
            (rs, row) -> new long[] {rs.getLong("id"), rs.getLong("human_id")});
        for (var account : accounts) {
            try {
                int total = 0;
                for (int round = 0; round < ROUNDS_PER_ACCOUNT; round++) {
                    var request = new HashMap<String, Object>();
                    request.put("account_id", account[0]); request.put("self_id", account[1]);
                    request.put("history", history.rows(account[0], account[1]));
                    int trained = models.train(request);
                    if (trained == 0) break;
                    total += trained;
                }
                if (total > 0) log.info("Participant training: account={}, interactions={}", account[0], total);
            } catch (RuntimeException exception) {
                log.warn("Participant training: account={}, errorType={}", account[0], exception.getClass().getSimpleName());
            }
        }
    }
}
