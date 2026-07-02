package hu.deposoft.webshop.domain.sidebar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A single managed sidebar block. `content` is type-specific JSON (see BlockType). */
@Entity
@Table(name = "sidebar_block")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SidebarBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_type", nullable = false)
    private BlockType blockType;

    @Setter
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Setter
    @Column(nullable = false)
    private boolean enabled = true;

    @Setter
    @Column(nullable = false)
    private String content;

    public static SidebarBlock create(BlockType type, int displayOrder, String content) {
        SidebarBlock b = new SidebarBlock();
        b.blockType = type;
        b.displayOrder = displayOrder;
        b.enabled = true;
        b.content = content;
        return b;
    }
}
