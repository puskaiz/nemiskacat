package hu.deposoft.webshop.domain.blog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class BlogTagRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired BlogTagRepository tags;
    @Autowired BlogPostRepository posts;

    @Test
    void savesAndFindsBySlug() {
        tags.save(BlogTag.create("Vintage", "vintage"));
        assertThat(tags.findBySlug("vintage")).isPresent();
        assertThat(tags.existsBySlug("vintage")).isTrue();
    }

    @Test
    void postTagsRoundTripThroughJoinTable() {
        BlogTag t = tags.save(BlogTag.create("Vintage", "vintage"));
        BlogPost p = BlogPost.create("cikk", "Cikk");
        p.setTags(new java.util.LinkedHashSet<>(java.util.List.of(t)));
        posts.save(p);
        BlogPost loaded = posts.findBySlug("cikk").orElseThrow();
        assertThat(loaded.getTags()).extracting(BlogTag::getSlug).containsExactly("vintage");
    }
}
