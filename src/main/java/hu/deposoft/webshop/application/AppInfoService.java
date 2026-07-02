package hu.deposoft.webshop.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Minimal application-layer service. Exists so the {@code api} layer has a real
 * collaborator to delegate to: controllers stay thin and business/derived data
 * is produced here (CLAUDE.md rule #1). Grows into real catalog/cart/checkout
 * services in later tasks.
 */
@Service
public class AppInfoService {

    private final String name;
    private final String version;

    public AppInfoService(
            @Value("${spring.application.name:webshop}") String name,
            @Value("${webshop.version:0.0.1-SNAPSHOT}") String version) {
        this.name = name;
        this.version = version;
    }

    public AppInfo currentInfo() {
        return new AppInfo(name, version);
    }

    public record AppInfo(String name, String version) {
    }
}
