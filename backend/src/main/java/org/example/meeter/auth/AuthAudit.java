package org.example.meeter.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AuthAudit {
    private final AuthEventRepository events;
    public AuthAudit(AuthEventRepository events) { this.events = events; }

    // Rejected credentials roll back login; the audit must survive that rollback.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rejected(AuthEvent.Type type, Long accountId, String username, String address, String agent) {
        events.save(new AuthEvent(type, accountId, null, username, address, agent));
    }
    // Successful state changes and their audit commit or roll back together.
    @Transactional(propagation = Propagation.MANDATORY)
    public void accepted(AuthEvent.Type type, User user, Long sessionRecordId, String address, String agent) {
        events.save(new AuthEvent(type, user.getId(), sessionRecordId, user.getUsername(), address, agent));
    }
}
