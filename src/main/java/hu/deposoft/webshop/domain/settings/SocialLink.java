package hu.deposoft.webshop.domain.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A site-wide social link (network + url), ordered for display. */
@Entity
@Table(name = "social_link")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false)
    private String network;

    @Setter
    @Column(nullable = false)
    private String url;

    @Setter
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public static SocialLink create(String network, String url, int displayOrder) {
        SocialLink s = new SocialLink();
        s.network = network;
        s.url = url;
        s.displayOrder = displayOrder;
        return s;
    }
}
