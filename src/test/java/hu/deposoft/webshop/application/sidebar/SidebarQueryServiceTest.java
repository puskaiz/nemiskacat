package hu.deposoft.webshop.application.sidebar;

import hu.deposoft.webshop.application.instagram.InstagramFeedQuery;
import hu.deposoft.webshop.application.instagram.InstagramPost;
import hu.deposoft.webshop.application.settings.SocialLinkQueryService;
import hu.deposoft.webshop.application.sidebar.SidebarQueryService.InstagramContent;
import hu.deposoft.webshop.application.sidebar.SidebarQueryService.SidebarBlockView;
import hu.deposoft.webshop.domain.blog.BlogCategoryRepository;
import hu.deposoft.webshop.domain.sidebar.BlockType;
import hu.deposoft.webshop.domain.sidebar.SidebarBlock;
import hu.deposoft.webshop.domain.sidebar.SidebarBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SidebarQueryServiceTest {

    @Mock SidebarBlockRepository blockRepository;
    @Mock BlogCategoryRepository categoryRepository;
    @Mock InstagramFeedQuery instagramFeedQuery;
    @Mock SocialLinkQueryService socialLinkQueryService;

    SidebarQueryService service;

    @BeforeEach
    void setUp() {
        service = new SidebarQueryService(blockRepository, categoryRepository,
                new ObjectMapper(), instagramFeedQuery, socialLinkQueryService);
    }

    private SidebarBlock instagramBlock(String contentJson) {
        return SidebarBlock.create(BlockType.INSTAGRAM, 1, contentJson);
    }

    private InstagramPost post(String id, String permalink, String displayUrl) {
        return new InstagramPost(id, null, InstagramPost.MediaType.IMAGE,
                displayUrl, permalink, Instant.now());
    }

    @Test
    void instagramBlockWithExplicitCountPassesCountToQuery() {
        List<InstagramPost> posts = List.of(
                post("1", "https://instagram.com/p/abc", "https://cdn.example.com/1.jpg"),
                post("2", "https://instagram.com/p/def", "https://cdn.example.com/2.jpg")
        );
        when(instagramFeedQuery.latestPosts(4)).thenReturn(posts);
        when(blockRepository.findByEnabledTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(instagramBlock("{\"title\":\"X\",\"count\":4}")));

        SidebarBlockView view = service.sidebar().blocks().get(0);

        assertThat(view.type()).isEqualTo("INSTAGRAM");
        InstagramContent ig = view.instagram();
        assertThat(ig).isNotNull();
        assertThat(ig.title()).isEqualTo("X");
        assertThat(ig.posts()).hasSize(2);
        assertThat(ig.posts().get(0).permalink()).isEqualTo("https://instagram.com/p/abc");
        verify(instagramFeedQuery).latestPosts(4);
    }

    @Test
    void instagramBlockWithoutCountUsesDefaultSix() {
        List<InstagramPost> posts = List.of(post("1", "https://instagram.com/p/a", "https://cdn.example.com/1.jpg"));
        when(instagramFeedQuery.latestPosts(6)).thenReturn(posts);
        when(blockRepository.findByEnabledTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(instagramBlock("{\"title\":\"Feed\"}")));

        SidebarBlockView view = service.sidebar().blocks().get(0);

        assertThat(view.instagram()).isNotNull();
        assertThat(view.instagram().title()).isEqualTo("Feed");
        verify(instagramFeedQuery).latestPosts(6);
    }

    @Test
    void instagramBlockWithEmptyFeedStillBuildsView() {
        when(instagramFeedQuery.latestPosts(anyInt())).thenReturn(List.of());
        when(blockRepository.findByEnabledTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(instagramBlock("{\"title\":\"X\",\"count\":6}")));

        SidebarBlockView view = service.sidebar().blocks().get(0);

        assertThat(view.instagram()).isNotNull();
        assertThat(view.instagram().posts()).isEmpty();
    }
}
