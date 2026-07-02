package hu.deposoft.webshop.domain.instagram;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstagramMediaRepository extends JpaRepository<InstagramMedia, String> {

    /**
     * Returns up to {@code limit} media rows ordered by display position ascending.
     * Used by {@link hu.deposoft.webshop.integrations.instagram.DbInstagramFeedQuery}.
     */
    List<InstagramMedia> findAllByOrderByPositionAsc(Limit limit);
}
