package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.application.catalog.ProductAdminEditService.EditorDisabledException;
import hu.deposoft.webshop.application.order.OrderAdminQueryService.NotFoundException;
import hu.deposoft.webshop.config.AdminProperties;
import hu.deposoft.webshop.domain.catalog.Attribute;
import hu.deposoft.webshop.domain.catalog.AttributeRepository;
import hu.deposoft.webshop.domain.catalog.AttributeValue;
import hu.deposoft.webshop.domain.catalog.AttributeValueRepository;
import hu.deposoft.webshop.domain.catalog.Slugs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Flag-gated: add a new term (value) to an existing global attribute. Idempotent on slug. */
@Service
@RequiredArgsConstructor
public class AttributeAdminService {

    private final AttributeRepository attributes;
    private final AttributeValueRepository attributeValues;
    private final ProductAdminQueryService query;
    private final AdminProperties adminProperties;

    @Transactional
    public ProductAdminQueryService.AttributeView addValue(Long attributeId, String label) {
        guard();
        Attribute attribute = attributes.findById(attributeId)
                .orElseThrow(() -> new NotFoundException("No attribute " + attributeId));
        String trimmed = label == null ? "" : label.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Label must not be blank");
        }
        String slug = Slugs.slugify(trimmed);
        if (slug.isEmpty()) {
            throw new IllegalArgumentException("Label has no slug-able characters: " + label);
        }
        if (attributeValues.findByAttributeAndSlug(attribute, slug).isEmpty()) {
            int nextSort = attribute.getValues().stream().mapToInt(AttributeValue::getSortOrder).max().orElse(-1) + 1;
            AttributeValue created = AttributeValue.create(attribute, slug, trimmed, nextSort);
            attribute.getValues().add(created);   // cascade ALL persists; keeps the in-memory view consistent
            attributes.save(attribute);
        }
        return query.attributes().stream().filter(a -> a.id().equals(attributeId)).findFirst().orElseThrow();
    }

    private void guard() {
        if (!adminProperties.productEditorEnabled()) {
            throw new EditorDisabledException("Product editor is disabled");
        }
    }
}
