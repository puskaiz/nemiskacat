package hu.deposoft.webshop.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the admin SPA (T15) from {@code classpath:/static/admin/} under
 * {@code /admin/**}. Real files (the bundle's JS/CSS, index.html) are served
 * directly; any other path (a client-side route like {@code /admin/workshops/edit/5})
 * falls back to {@code index.html} so the browser router can handle it. Access is
 * public — the data behind {@code /api/admin/**} is what requires the ADMIN role.
 */
@Configuration
public class AdminSpaConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/admin/**")
                .addResourceLocations("classpath:/static/admin/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        return location.createRelative("index.html"); // SPA client-side route
                    }
                });
    }
}
