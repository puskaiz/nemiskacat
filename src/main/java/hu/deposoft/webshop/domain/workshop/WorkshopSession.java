package hu.deposoft.webshop.domain.workshop;

import hu.deposoft.webshop.domain.catalog.Variant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** A concrete workshop occurrence: the sellable {@link Variant} plus its start time and seat capacity. */
@Entity
@Table(name = "workshop_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkshopSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "variant_id", nullable = false, unique = true)
    private Variant variant;

    @Setter
    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Setter
    @Column(nullable = false)
    private int capacity;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public static WorkshopSession create(Variant variant, OffsetDateTime startAt, int capacity) {
        WorkshopSession s = new WorkshopSession();
        s.variant = variant;
        s.startAt = startAt;
        s.capacity = capacity;
        return s;
    }

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
