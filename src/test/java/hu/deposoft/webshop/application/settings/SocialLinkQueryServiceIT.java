package hu.deposoft.webshop.application.settings;

import hu.deposoft.webshop.application.settings.SocialLinkQueryService.SocialLinkView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@Transactional
class SocialLinkQueryServiceIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @Autowired SocialLinkQueryService social;

    @Test
    void listsSeededLinksInOrder() {
        assertThat(social.links()).extracting(SocialLinkView::network)
                .containsExactly("facebook", "instagram", "youtube");
    }
}
