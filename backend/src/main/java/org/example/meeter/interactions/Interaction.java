package org.example.meeter.interactions;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import lombok.*;
import org.example.meeter.*;
import org.example.meeter.auth.User;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "interactions", indexes = @Index(name = "idx_interaction_dates", columnList = "start_date,end_date"))
@Inheritance(strategy = InheritanceType.JOINED)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class Interaction extends OwnedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid = UUID.randomUUID();
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false, length = 128)
    private String name;
    @Column(length = 10240)
    private String longDesc;
    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;
    @Column(name = "end_date")
    private LocalDateTime endDate;
    @ManyToOne(fetch = FetchType.LAZY)
    private Place place;
    @OneToMany(mappedBy = "interaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    @Getter(AccessLevel.NONE)
    private Set<InteractionParticipant> participantLinks = new LinkedHashSet<>();

    public Optional<InteractionParticipant> participantLink(Long humanId) {
        return participantLinks.stream().filter(link -> link.getHuman().getId().equals(humanId)).findFirst();
    }

    public void addParticipant(Human human, User creator) {
        if (participantLink(human.getId()).isPresent()) return;
        InteractionParticipant link = new InteractionParticipant(this, human);
        link.recordCreator(creator);
        participantLinks.add(link);
    }

    public void removeParticipant(InteractionParticipant link) {
        participantLinks.remove(link);
    }

    public boolean canRemoveParticipant(InteractionParticipant link, Long accountId, Long humanId) {
        return link.getHuman().getId().equals(humanId) || link.getCreatedBy().getId().equals(accountId);
    }

    public Set<Human> getParticipants() {
        Set<Human> people = new LinkedHashSet<>();
        participantLinks.forEach(link -> people.add(link.getHuman()));
        return Collections.unmodifiableSet(people);
    }

    @Override public void assignOwner(User owner) {
        super.assignOwner(owner);
        participantLinks.forEach(link -> link.recordCreator(owner));
    }

    protected Interaction(String name, String longDesc, LocalDateTime startDate,
                          LocalDateTime endDate, Collection<Human> participants, Place place) {
        this.name = name;
        this.longDesc = longDesc;
        this.startDate = startDate;
        this.endDate = endDate;
        new LinkedHashSet<>(participants).forEach(human -> participantLinks.add(new InteractionParticipant(this, human)));
        this.place = place;
    }

    public abstract InteractionType getType();

    void edit(String name, String description, LocalDateTime start, LocalDateTime end, Place place) {
        this.name = name;
        this.longDesc = description;
        this.startDate = start;
        this.endDate = end;
        this.place = place;
    }

    public LocalDate getInteractionPointsDate() {
        return (endDate == null ? startDate : endDate).toLocalDate();
    }
}
