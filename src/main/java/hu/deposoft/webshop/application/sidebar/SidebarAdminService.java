package hu.deposoft.webshop.application.sidebar;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.application.sidebar.SidebarQueryService.AuthorContent;
import hu.deposoft.webshop.application.sidebar.SidebarQueryService.ContactContent;
import hu.deposoft.webshop.application.sidebar.SidebarQueryService.CtaContent;
import hu.deposoft.webshop.application.sidebar.SidebarQueryService.SocialContent;
import hu.deposoft.webshop.domain.blog.BlogCategory;
import hu.deposoft.webshop.domain.blog.BlogCategoryRepository;
import hu.deposoft.webshop.domain.sidebar.BlockType;
import hu.deposoft.webshop.domain.sidebar.SidebarBlock;
import hu.deposoft.webshop.domain.sidebar.SidebarBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SidebarAdminService {

    private final SidebarBlockRepository blocks;
    private final BlogCategoryRepository categories;
    private final ObjectMapper objectMapper;
    private final AuditService audit;

    public record SidebarBlockView(Long id, String blockType, int displayOrder,
                                   boolean enabled, String content) {}
    public record ContentUpdate(String content) {}
    public record ReorderRequest(List<Long> blockIds) {}
    public record CategoryVisibility(String name, String slug, boolean sidebarHidden) {}
    public record VisibilityUpdate(boolean hidden) {}

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String m) { super(m); }
    }

    @Transactional(readOnly = true)
    public List<SidebarBlockView> list() {
        return blocks.findAllByOrderByDisplayOrderAsc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public SidebarBlockView get(Long id) {
        return toView(find(id));
    }

    public SidebarBlockView updateContent(Long id, ContentUpdate cmd) {
        SidebarBlock b = find(id);
        validateContent(b.getBlockType(), cmd.content());
        b.setContent(cmd.content());
        audit.record("SIDEBAR_BLOCK_UPDATE", "sidebar_block", String.valueOf(id), b.getBlockType().name());
        return toView(b);
    }

    public SidebarBlockView setEnabled(Long id, boolean enabled) {
        SidebarBlock b = find(id);
        b.setEnabled(enabled);
        audit.record(enabled ? "SIDEBAR_BLOCK_ENABLE" : "SIDEBAR_BLOCK_DISABLE",
                "sidebar_block", String.valueOf(id), b.getBlockType().name());
        return toView(b);
    }

    public List<SidebarBlockView> reorder(List<Long> blockIds) {
        List<SidebarBlock> current = blocks.findAllByOrderByDisplayOrderAsc();
        if (blockIds.size() != current.size()
                || !blockIds.stream().sorted().toList()
                    .equals(current.stream().map(SidebarBlock::getId).sorted().toList())) {
            throw new IllegalArgumentException("Reorder list must contain exactly the existing block ids");
        }
        for (int i = 0; i < blockIds.size(); i++) {
            Long bid = blockIds.get(i);
            SidebarBlock b = current.stream().filter(x -> x.getId().equals(bid)).findFirst().orElseThrow();
            b.setDisplayOrder(i + 1);
        }
        audit.record("SIDEBAR_BLOCK_REORDER", "sidebar_block", "*", blockIds.toString());
        return list();
    }

    @Transactional(readOnly = true)
    public List<CategoryVisibility> categories() {
        return categories.findAllByOrderByNameAsc().stream()
                .map(c -> new CategoryVisibility(c.getName(), c.getSlug(), c.isSidebarHidden()))
                .toList();
    }

    public CategoryVisibility setCategoryVisibility(String slug, boolean hidden) {
        BlogCategory c = categories.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Blog category not found: " + slug));
        c.setSidebarHidden(hidden);
        audit.record("SIDEBAR_CATEGORY_VISIBILITY", "blog_category", slug, hidden ? "hidden" : "visible");
        return new CategoryVisibility(c.getName(), c.getSlug(), c.isSidebarHidden());
    }

    // Title-only shape for a CATEGORIES block (the category list itself is loaded live).
    private record CategoriesMetaContent(String title) {}

    // Minimal content shape for an INSTAGRAM block (posts are loaded live; count is optional).
    private record InstagramMetaContent(String title, Integer count) {}

    /**
     * Rejects content that isn't valid, correctly-typed JSON for the block: strict parsing
     * (unknown/foreign fields fail) plus a non-blank primary field. Prevents a mis-typed or
     * empty save from rendering blank fields into the public sidebar.
     */
    private void validateContent(BlockType type, String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("content must not be empty");
        }
        try {
            switch (type) {
                case AUTHOR -> requireText(strict(json, AuthorContent.class).name(), "name");
                case CTA -> requireText(strict(json, CtaContent.class).title(), "title");
                case CONTACT -> requireText(strict(json, ContactContent.class).title(), "title");
                case SOCIAL -> requireText(strict(json, SocialContent.class).title(), "title");
                case CATEGORIES -> requireText(strict(json, CategoriesMetaContent.class).title(), "title");
                case INSTAGRAM -> requireText(strict(json, InstagramMetaContent.class).title(), "title");
            }
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid content JSON for " + type + ": " + e.getOriginalMessage());
        }
    }

    private <T> T strict(String json, Class<T> type) {
        return objectMapper.readerFor(type)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(json);
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private SidebarBlock find(Long id) {
        return blocks.findById(id)
                .orElseThrow(() -> new NotFoundException("Sidebar block not found: " + id));
    }

    private SidebarBlockView toView(SidebarBlock b) {
        return new SidebarBlockView(b.getId(), b.getBlockType().name(),
                b.getDisplayOrder(), b.isEnabled(), b.getContent());
    }
}
