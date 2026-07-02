package hu.deposoft.webshop.application.sidebar;

import hu.deposoft.webshop.application.blog.BlogQueryService.CategoryRef;
import hu.deposoft.webshop.application.instagram.InstagramFeedQuery;
import hu.deposoft.webshop.application.instagram.InstagramPost;
import hu.deposoft.webshop.application.settings.SocialLinkQueryService;
import hu.deposoft.webshop.domain.blog.BlogCategoryRepository;
import hu.deposoft.webshop.domain.sidebar.SidebarBlock;
import hu.deposoft.webshop.domain.sidebar.SidebarBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SidebarQueryService {

    private static final int DEFAULT_INSTAGRAM_COUNT = 6;

    private final SidebarBlockRepository blocks;
    private final BlogCategoryRepository categories;
    private final ObjectMapper objectMapper;
    private final InstagramFeedQuery instagramFeedQuery;
    private final SocialLinkQueryService socialLinks;

    public record AuthorContent(String name, String bio, String photoUrl, String goals) {}
    public record CategoriesContent(String title, List<CategoryRef> items) {}
    public record CtaContent(String title, String buttonLabel, String url, String imageUrl, String description) {}
    public record ContactContent(String title, String phone, String email, String address, String openingHours) {}
    public record SocialLink(String network, String url) {}
    public record SocialContent(String title, List<SocialLink> links) {}
    public record InstagramContent(String title, List<InstagramPost> posts) {}

    public record SidebarBlockView(String type, AuthorContent author,
                                   CategoriesContent categories, CtaContent cta,
                                   ContactContent contact, SocialContent social,
                                   InstagramContent instagram) {}

    public record SidebarView(List<SidebarBlockView> blocks) {}

    // JSON shape for the CATEGORIES block (the list itself is loaded live).
    private record CategoriesMeta(String title) {}

    // JSON shape for the SOCIAL block (links are loaded live from SocialLinkQueryService).
    private record SocialMeta(String title) {}

    // JSON shape for the INSTAGRAM block (posts are loaded live from InstagramFeedQuery).
    private record InstagramMeta(String title, Integer count) {}

    public SidebarView sidebar() {
        List<SidebarBlockView> views = blocks.findByEnabledTrueOrderByDisplayOrderAsc()
                .stream().map(this::toView).toList();
        return new SidebarView(views);
    }

    private SidebarBlockView toView(SidebarBlock b) {
        String json = b.getContent();
        return switch (b.getBlockType()) {
            case AUTHOR -> new SidebarBlockView("AUTHOR",
                    objectMapper.readValue(json, AuthorContent.class), null, null, null, null, null);
            case CATEGORIES -> {
                CategoriesMeta meta = objectMapper.readValue(json, CategoriesMeta.class);
                List<CategoryRef> items = categories.findBySidebarHiddenFalseOrderByNameAsc()
                        .stream().map(c -> new CategoryRef(c.getName(), c.getSlug())).toList();
                yield new SidebarBlockView("CATEGORIES", null,
                        new CategoriesContent(meta.title(), items), null, null, null, null);
            }
            case CTA -> new SidebarBlockView("CTA", null, null,
                    objectMapper.readValue(json, CtaContent.class), null, null, null);
            case CONTACT -> new SidebarBlockView("CONTACT", null, null, null,
                    objectMapper.readValue(json, ContactContent.class), null, null);
            case SOCIAL -> {
                SocialMeta meta = objectMapper.readValue(json, SocialMeta.class);
                List<SocialLink> storeLinks = socialLinks.links().stream()
                        .map(s -> new SocialLink(s.network(), s.url()))
                        .toList();
                yield new SidebarBlockView("SOCIAL", null, null, null, null,
                        new SocialContent(meta.title(), storeLinks), null);
            }
            case INSTAGRAM -> {
                InstagramMeta meta = objectMapper.readValue(json, InstagramMeta.class);
                int count = meta.count() != null ? meta.count() : DEFAULT_INSTAGRAM_COUNT;
                List<InstagramPost> posts = instagramFeedQuery.latestPosts(count);
                yield new SidebarBlockView("INSTAGRAM", null, null, null, null, null,
                        new InstagramContent(meta.title(), posts));
            }
        };
    }
}
