package org.example.meeter;

import jakarta.persistence.*;
import lombok.Getter;
import org.example.meeter.auth.User;

@MappedSuperclass
@Getter
public abstract class OwnedEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private User createdBy;

    public void assignOwner(User user) {
        if (createdBy != null) throw new IllegalStateException("Właściciel jest już przypisany.");
        createdBy = user;
    }
}
