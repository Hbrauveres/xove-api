package dev.kryora.live.access;

import dev.kryora.live.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "access_requests")
public class AccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessRequestStatus status = AccessRequestStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "decided_at")
    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    protected AccessRequest() {
        // required by JPA
    }

    public AccessRequest(User user, String message) {
        this.user = user;
        this.message = message;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getMessage() { return message; }
    public AccessRequestStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public User getDecidedBy() { return decidedBy; }

    public void decide(AccessRequestStatus decision, User admin) {
        this.status = decision;
        this.decidedBy = admin;
        this.decidedAt = Instant.now();
    }
}
