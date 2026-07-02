package hu.deposoft.webshop.integrations.instagram;

import hu.deposoft.webshop.domain.instagram.InstagramMedia;
import hu.deposoft.webshop.domain.instagram.InstagramMediaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Limit;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers IT for {@link InstagramMediaRepository}: verifies ordering by
 * {@code position} and limit enforcement.
 */
@SpringBootTest
@Testcontainers
@Transactional
class InstagramMediaRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    InstagramMediaRepository repo;

    private InstagramMedia media(String id, int position) {
        return InstagramMedia.create(
                id,
                "Caption for " + id,
                "IMAGE",
                "https://example.com/" + id + ".jpg",
                "https://www.instagram.com/p/" + id,
                Instant.parse("2024-01-01T12:00:00Z"),
                position,
                Instant.now());
    }

    @Test
    void findsLatestNOrderedByPositionAsc() {
        repo.saveAll(List.of(
                media("c", 3),
                media("a", 1),
                media("b", 2),
                media("d", 4)));

        List<InstagramMedia> result = repo.findAllByOrderByPositionAsc(Limit.of(3));

        assertThat(result).extracting(InstagramMedia::getId)
                .containsExactly("a", "b", "c");
    }

    @Test
    void limitIsRespected() {
        repo.saveAll(List.of(media("x", 1), media("y", 2), media("z", 3)));

        List<InstagramMedia> result = repo.findAllByOrderByPositionAsc(Limit.of(2));

        assertThat(result).hasSize(2);
    }

    @Test
    void emptyRepoReturnsEmptyList() {
        List<InstagramMedia> result = repo.findAllByOrderByPositionAsc(Limit.of(10));
        assertThat(result).isEmpty();
    }
}
