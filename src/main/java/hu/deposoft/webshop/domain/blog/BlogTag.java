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

/** Managed blog tag (migrated from WordPress post_tag). Slug stable (CLAUDE.md #7). */
@Entity
@Table(name = "blog_tag")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BlogTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    public static BlogTag create(String name, String slug) {
        if (!BlogPost.isValidSlug(slug)) {
            throw new IllegalArgumentException("Invalid blog tag slug: " + slug);
        }
        BlogTag t = new BlogTag();
        t.name = name;
        t.slug = slug;
        return t;
    }
}
