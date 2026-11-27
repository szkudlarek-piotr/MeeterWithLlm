package org.example.meeter.publicapi;

import jakarta.persistence.*;
import java.util.*;
import org.example.meeter.*;
import org.example.meeter.activity.PhysicalActivity;
import org.example.meeter.auth.User;
import org.example.meeter.interactions.Interaction;
import org.example.meeter.news.NewsEntry;
import org.example.meeter.quote.Quote;
import org.example.meeter.quote.game.*;
import org.example.meeter.superpowers.Superpower;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @Transactional(readOnly = true)
public class PublicIdentifiers {
    public enum Kind {
        HUMAN(Human.class), PLACE(Place.class), CLIQUE(Clique.class), INTERACTION(Interaction.class),
        QUOTE(Quote.class), GAME(QuoteGame.class), ROUND(GameRound.class), USER(User.class), NEWS(NewsEntry.class),
        SUPERPOWER(Superpower.class), ACTIVITY(PhysicalActivity.class);
        final Class<?> entity;
        Kind(Class<?> entity) { this.entity = entity; }
    }
    @PersistenceContext private EntityManager entities;
    public Long internal(Kind kind, Object external) {
        if (!(external instanceof String text) || !text.matches("(?i)[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Wymagany jest identyfikator UUID.");
        UUID uuid = UUID.fromString(text);
        return entities.createQuery("select e.id from " + kind.entity.getSimpleName() + " e where e.uuid = :uuid", Long.class)
            .setParameter("uuid", uuid).getResultStream().findFirst()
            .orElseThrow(PublicIdentifiers::missing);
    }
    // One lookup per entity kind/page, never one database query per portrait/card.
    public Map<Kind, Map<Long, UUID>> external(Map<Kind, Set<Long>> requested) {
        Map<Kind, Map<Long, UUID>> result = new EnumMap<>(Kind.class);
        requested.forEach((kind, ids) -> {
            Map<Long, UUID> values = new HashMap<>();
            if (!ids.isEmpty()) {
                for (Object[] row : entities.createQuery("select e.id, e.uuid from " + kind.entity.getSimpleName()
                        + " e where e.id in :ids", Object[].class).setParameter("ids", ids).getResultList()) {
                    values.put((Long) row[0], (UUID) row[1]);
                }
            }
            if (values.size() != ids.size()) throw new IllegalStateException("Missing persistent public identifier");
            result.put(kind, values);
        });
        return result;
    }
    public static Kind field(String name) {
        return switch (name) {
            case "humanId", "authorId", "groomId", "brideId", "participantIds", "guestIds", "removableParticipantIds" -> Kind.HUMAN;
            case "placeId", "homeId", "cliqueCapitalId", "ceremonyPlaceId", "partyPlaceId", "hotelId", "placeIds" -> Kind.PLACE;
            case "cliqueId", "cliqueIds" -> Kind.CLIQUE;
            case "interactionId", "photoEventId", "visitId" -> Kind.INTERACTION;
            case "roundId" -> Kind.ROUND;
            case "gameId" -> Kind.GAME;
            case "playerId" -> Kind.USER;
            case "superpowerId", "superpowerIds" -> Kind.SUPERPOWER;
            default -> null;
        };
    }
    public static ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Nie znaleziono rekordu.");
    }
}
