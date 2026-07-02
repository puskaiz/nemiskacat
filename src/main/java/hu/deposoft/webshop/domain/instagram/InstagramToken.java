package hu.deposoft.webshop.domain.instagram;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Singleton row storing the long-lived Instagram access token.
 * The PK is always {@code 1} (enforced by a DB check constraint).
 * Token refresh is a later task (Task 3).
 */
@Entity
@Table(name = "instagram_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InstagramToken {

    public static final short SINGLETON_ID = 1;

    @Id
    private short id = SINGLETON_ID;

    @Setter
    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Setter
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static InstagramToken create(String accessToken, Instant updatedAt) {
        InstagramToken t = new InstagramToken();
        t.accessToken = accessToken;
        t.updatedAt = updatedAt;
        return t;
    }

    public void update(String accessToken, Instant expiresAt, Instant updatedAt) {
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
        this.updatedAt = updatedAt;
    }
}
