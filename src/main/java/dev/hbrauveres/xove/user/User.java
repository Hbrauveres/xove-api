package dev.hbrauveres.xove.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "google_subject", unique = true)
    private String googleSubject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.NONE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {
        // required by JPA
    }

    public User(String email, String name, String avatarUrl) {
        this.email = normalizeEmail(email);
        this.name = name;
        this.avatarUrl = avatarUrl;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getGoogleSubject() { return googleSubject; }
    public UserStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void updateProfile(String name, String avatarUrl) {
        this.name = name;
        this.avatarUrl = avatarUrl;
    }

    public void linkGoogleAccount(String googleSubject) {
        this.googleSubject = googleSubject;
    }

    public void changeStatus(UserStatus status) {
        this.status = status;
    }
}
