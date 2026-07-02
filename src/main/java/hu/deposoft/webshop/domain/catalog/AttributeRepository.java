package hu.deposoft.webshop.domain.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AttributeRepository extends JpaRepository<Attribute, Long> {

    Optional<Attribute> findByExternalId(Long externalId);

    Optional<Attribute> findBySlug(String slug);

    /** Fetch all attributes with their values in a single query (JOIN FETCH bypasses L1 cache for collection). */
    @Query("SELECT DISTINCT a FROM Attribute a LEFT JOIN FETCH a.values ORDER BY a.slug")
    List<Attribute> findAllWithValues();
}
