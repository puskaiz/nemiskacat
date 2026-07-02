package hu.deposoft.webshop.application.blog;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.domain.blog.BlogTag;
import hu.deposoft.webshop.domain.blog.BlogTagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BlogTagAdminService {

    private final BlogTagRepository tags;
    private final AuditService audit;

    public record TagView(Long id, String name, String slug) {}
    public record TagUpsert(String name, String slug) {}
    public record RenameRequest(String name) {}

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String m) { super(m); }
    }

    @Transactional(readOnly = true)
    public TagView getTag(Long id) {
        BlogTag t = find(id);
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
        if (cmd.slug() == null || cmd.slug().isBlank()) {
            throw new IllegalArgumentException("slug must not be blank");
        }
        if (tags.existsBySlug(cmd.slug())) {
            throw new IllegalArgumentException("Blog tag slug already exists: " + cmd.slug());
        }
        BlogTag t = tags.save(BlogTag.create(cmd.name().trim(), cmd.slug())); // create() validates the slug
        audit.record("BLOG_TAG_CREATE", "blog_tag", String.valueOf(t.getId()), t.getSlug());
        return new TagView(t.getId(), t.getName(), t.getSlug());
    }

    public TagView rename(Long id, RenameRequest cmd) {
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        BlogTag t = find(id);
        t.setName(cmd.name().trim());   // slug immutable (CLAUDE.md #7)
        audit.record("BLOG_TAG_UPDATE", "blog_tag", String.valueOf(id), t.getSlug());
        return new TagView(t.getId(), t.getName(), t.getSlug());
    }

    public void delete(Long id) {
        BlogTag t = find(id);
        tags.delete(t);
        audit.record("BLOG_TAG_DELETE", "blog_tag", String.valueOf(id), t.getSlug());
    }

    private BlogTag find(Long id) {
        return tags.findById(id).orElseThrow(() -> new NotFoundException("Blog tag not found: " + id));
    }
}
