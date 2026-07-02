package hu.deposoft.webshop.api;

import hu.deposoft.webshop.application.AppInfoService;
import hu.deposoft.webshop.application.AppInfoService.AppInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Thin REST controller for the {@code api} layer. Delegates to the application
 * service and holds no business logic (CLAUDE.md rule #1). Serves as the seed of
 * the JSON API used later by admin, POS, static-page islands and feeds.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InfoController {

    private final AppInfoService appInfoService;

    @GetMapping("/info")
    public AppInfo info() {
        return appInfoService.currentInfo();
    }
}
