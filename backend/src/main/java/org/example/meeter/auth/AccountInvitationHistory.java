package org.example.meeter.auth;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "account_invitation_history")
class AccountInvitationHistory {
    @Id
    private Long invitedUserId;
    @Column(nullable = false)
    private Long inviterUserId;
    @Column(nullable = false)
    private Long invitedHumanId;
    @Column(nullable = false)
    private Instant acceptedAt;

    protected AccountInvitationHistory() {}

    AccountInvitationHistory(Long invitedUserId, Long inviterUserId, Long invitedHumanId, Instant acceptedAt) {
        this.invitedUserId = invitedUserId;
        this.inviterUserId = inviterUserId;
        this.invitedHumanId = invitedHumanId;
        this.acceptedAt = acceptedAt;
    }
}
