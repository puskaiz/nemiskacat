package hu.deposoft.webshop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Webshop settings. {@code imagesBaseUrl} maps the interim wp/<path> storage keys
 * to servable URLs until object storage exists; {@code canonicalBaseUrl} feeds the
 * canonical links and JSON-LD URLs (the WP URL scheme is preserved 1:1);
 * {@code siteName} resolves the Yoast {@code %%sitename%%} placeholder in imported
 * SEO titles.
 */
@ConfigurationProperties(prefix = "webshop")
public record WebshopProperties(String imagesBaseUrl, String canonicalBaseUrl,
                                @DefaultValue("Nemiskacat") String siteName) {

    /** Resolves a stored image key to a servable URL. Uploaded originals ({@code up/…}) are served
     *  by this app at {@code /media}; legacy imported keys ({@code wp/…}) come from the configured
     *  images base. */
    public String imageUrl(String storageKey) {
        if (storageKey.startsWith("up/")) {
            return "/media/" + storageKey;
        }
        String path = storageKey.startsWith("wp/") ? storageKey.substring(3) : storageKey;
        return imagesBaseUrl + "/" + path;
    }
}
