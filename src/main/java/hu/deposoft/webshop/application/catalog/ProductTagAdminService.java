package hu.deposoft.webshop.application.catalog;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.domain.blog.BlogPost;
import hu.deposoft.webshop.domain.catalog.ProductTag;
import hu.deposoft.webshop.domain.catalog.ProductTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ProductTagAdminService {

    private final ProductTagRepository tags;
    private final AuditService audit;

    public record TagView(Long id, String name, String slug) {}
    public record TagUpsert(String name, String slug) {}
    public record RenameRequest(String name) {}

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String m) { super(m); }
    }

    @Transactional(readOnly = true)
    public TagView getById(Long id) {
        ProductTag t = find(id);
        return new TagView(t.getId(), t.getName(), t.getSlug());
    }

    @Transactional(readOnly = true)
    public List<TagView> list() {
        return tags.findAllByOrderByNameAsc().stream()
                .map(t -> new TagView(t.getId(), t.getName(), t.getSlug())).toList();
    }

    public TagView create(TagUpsert cmd) {
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (!BlogPost.isValidSlug(cmd.slug())) {
            throw new IllegalArgumentException("Invalid product tag slug: " + cmd.slug());
        }
        if (tags.existsBySlug(cmd.slug())) {
            throw new IllegalArgumentException("Product tag slug already exists: " + cmd.slug());
        }
        ProductTag t = tags.save(ProductTag.createManual(cmd.slug(), cmd.name().trim()));
        audit.record("PRODUCT_TAG_CREATE", "product_tag", String.valueOf(t.getId()), t.getSlug());
        return new TagView(t.getId(), t.getName(), t.getSlug());
    }

    public TagView rename(Long id, RenameRequest cmd) {
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        ProductTag t = find(id);
        t.setName(cmd.name().trim());   // slug immutable (CLAUDE.md #7)
        audit.record("PRODUCT_TAG_UPDATE", "product_tag", String.valueOf(id), t.getSlug());
        return new TagView(t.getId(), t.getName(), t.getSlug());
    }

    public void delete(Long id) {
        ProductTag t = find(id);
        tags.delete(t);
        audit.record("PRODUCT_TAG_DELETE", "product_tag", String.valueOf(id), t.getSlug());
    }

    private ProductTag find(Long id) {
        return tags.findById(id).orElseThrow(() -> new NotFoundException("Product tag not found: " + id));
    }
}
