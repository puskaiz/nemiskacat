package hu.deposoft.webshop.domain.sidebar;

import hu.deposoft.webshop.domain.blog.BlogCategory;
import hu.deposoft.webshop.domain.blog.BlogCategoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class SidebarBlockRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired SidebarBlockRepository blocks;
    @Autowired BlogCategoryRepository categories;

    @Test
    void seedExposesFiveEnabledBlocksInOrder() {
        List<SidebarBlock> enabled = blocks.findByEnabledTrueOrderByDisplayOrderAsc();
        assertThat(enabled).extracting(SidebarBlock::getBlockType)
                .containsExactly(BlockType.AUTHOR, BlockType.CATEGORIES,
                        BlockType.CTA, BlockType.CONTACT, BlockType.SOCIAL);
    }

    @Test
    void disabledBlockIsExcluded() {
        SidebarBlock social = blocks.findByEnabledTrueOrderByDisplayOrderAsc()
                .stream().filter(b -> b.getBlockType() == BlockType.SOCIAL).findFirst().orElseThrow();
        social.setEnabled(false);
        blocks.save(social);
        assertThat(blocks.findByEnabledTrueOrderByDisplayOrderAsc())
                .extracting(SidebarBlock::getBlockType).doesNotContain(BlockType.SOCIAL);
    }

    @Test
    void categoryFinderExcludesHiddenAndSortsByName() {
        categories.save(BlogCategory.create("Zebra", "zebra"));
        BlogCategory hidden = BlogCategory.create("Alma", "alma");
        hidden.setSidebarHidden(true);
        categories.save(hidden);
        categories.save(BlogCategory.create("Béka", "beka"));

        List<BlogCategory> visible = categories.findBySidebarHiddenFalseOrderByNameAsc();
        assertThat(visible).extracting(BlogCategory::getName)
                .containsSubsequence("Béka", "Zebra")   // alphabetical
                .doesNotContain("Alma");                  // hidden excluded
    }
}
