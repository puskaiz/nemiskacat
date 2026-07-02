package hu.deposoft.webshop.domain.blog;

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

/** Managed blog category. Slug is stable (used in /blog/kategoria/{slug}). */
@Entity
@Table(name = "blog_category")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlogCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Setter
    @Column(name = "sidebar_hidden", nullable = false)
    private boolean sidebarHidden = false;

    public static BlogCategory create(String name, String slug) {
        if (!BlogPost.isValidSlug(slug)) {
            throw new IllegalArgumentException("Invalid blog category slug: " + slug);
        }
        BlogCategory c = new BlogCategory();
        c.name = name;
        c.slug = slug;
        return c;
    }
}
