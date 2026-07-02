package hu.deposoft.webshop.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebshopPropertiesTest {

    private final WebshopProperties props =
            new WebshopProperties("http://cdn.example/up", "http://shop.example", "Nemiskacat");

    @Test
    void uploadedKeyRoutesToMedia() {
        assertThat(props.imageUrl("up/abc.jpg")).isEqualTo("/media/up/abc.jpg");
    }

    @Test
    void legacyWpKeyRoutesToImagesBase() {
        assertThat(props.imageUrl("wp/2023/05/x.jpg")).isEqualTo("http://cdn.example/up/2023/05/x.jpg");
    }
}
