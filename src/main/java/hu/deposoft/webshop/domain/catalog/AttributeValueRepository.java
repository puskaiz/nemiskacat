package hu.deposoft.webshop.domain.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttributeValueRepository extends JpaRepository<AttributeValue, Long> {

    Optional<AttributeValue> findByAttributeAndSlug(Attribute attribute, String slug);
}
