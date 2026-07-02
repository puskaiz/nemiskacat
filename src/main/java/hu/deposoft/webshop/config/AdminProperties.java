package hu.deposoft.webshop.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Admin-side feature flags. The product editor is off until the Woo cutover (ADR 0005). */
@ConfigurationProperties(prefix = "webshop.admin")
public record AdminProperties(@DefaultValue("true") boolean productEditorEnabled) {
}
