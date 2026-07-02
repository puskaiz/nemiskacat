package hu.deposoft.webshop.domain.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * One payment attempt at the gateway. An order may have several attempts (retry
 * after a rejected card); {@code payId} is the bank's correlation key.
 */
@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    public enum State {INITIATED, CONFIRMED, FAILED, REVERSED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "pay_id", nullable = false, unique = true)
    private String payId;

    @Column(name = "amount_huf", nullable = false)
    private long amountHuf;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state = State.INITIATED;

    @Column(name = "result_message")
    private String resultMessage;

    /** Marks the "paid but order recording failed" branch for ops alerting (T23). */
    @Column(nullable = false)
    private boolean alert;

    @Column(name = "last_checked_at")
    private OffsetDateTime lastCheckedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static Payment initiate(Order order, String payId, long amountHuf) {
        Payment p = new Payment();
        p.order = order;
        p.payId = payId;
        p.amountHuf = amountHuf;
        return p;
    }

    public boolean isTerminal() {
        return state != State.INITIATED;
    }

    public void markState(State newState, String message) {
        this.state = newState;
        this.resultMessage = message;
    }

    public void raiseAlert(String message) {
        this.alert = true;
        this.resultMessage = message;
    }

    public void touchChecked(OffsetDateTime at) {
        this.lastCheckedAt = at;
    }

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
