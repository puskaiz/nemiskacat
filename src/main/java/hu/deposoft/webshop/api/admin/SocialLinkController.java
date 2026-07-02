package hu.deposoft.webshop.api.admin;

import hu.deposoft.webshop.application.settings.SocialLinkAdminService;
import hu.deposoft.webshop.application.settings.SocialLinkAdminService.ReorderRequest;
import hu.deposoft.webshop.application.settings.SocialLinkAdminService.SocialLinkUpsert;
import hu.deposoft.webshop.application.settings.SocialLinkAdminService.SocialLinkView;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SocialLinkController {

    private final SocialLinkAdminService service;

    @GetMapping("/api/admin/settings/social")
    public List<SocialLinkView> list(HttpServletResponse response) {
        List<SocialLinkView> all = service.list();
        response.setHeader("X-Total-Count", String.valueOf(all.size()));
        return all;
    }

    @PostMapping("/api/admin/settings/social")
    public SocialLinkView create(@RequestBody SocialLinkUpsert cmd) {
        return service.create(cmd);
    }

    @PutMapping("/api/admin/settings/social/{id}")
    public SocialLinkView update(@PathVariable Long id, @RequestBody SocialLinkUpsert cmd) {
        return service.update(id, cmd);
    }

    @DeleteMapping("/api/admin/settings/social/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/admin/settings/social/reorder")
    public List<SocialLinkView> reorder(@RequestBody ReorderRequest body) {
        return service.reorder(body.ids());
    }
}
