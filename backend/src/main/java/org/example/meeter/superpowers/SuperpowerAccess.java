package org.example.meeter.superpowers;

import org.example.meeter.Human;
import org.example.meeter.auth.CurrentAccount;
import org.example.meeter.interactions.InteractionRepository;
import org.springframework.stereotype.Component;

/** Shared rules for who may manage a person's superpower links. */
@Component
public class SuperpowerAccess {
    private final CurrentAccount account;
    private final InteractionRepository interactions;

    public SuperpowerAccess(CurrentAccount account, InteractionRepository interactions) {
        this.account = account;
        this.interactions = interactions;
    }

    /** Administrators, the person themselves, the person's creator and the person's spouse. */
    public boolean canManage(Human human) {
        if (account.isAdmin()) return true;
        if (human.getCreatedBy() != null && human.getCreatedBy().getId().equals(account.id())) return true;
        Long selfId = account.principal().humanId();
        if (selfId == null) return false;
        if (selfId.equals(human.getId())) return true;
        return interactions.countWeddingBetween(human.getId(), selfId) > 0;
    }
}
