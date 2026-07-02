package hu.deposoft.webshop.web;

import hu.deposoft.webshop.application.settings.SocialLinkQueryService;
import hu.deposoft.webshop.application.settings.SocialLinkQueryService.SocialLinkView;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/** Exposes the site-wide social links to every Thymeleaf view (header fragment). */
@ControllerAdvice(basePackages = "hu.deposoft.webshop.web")
@RequiredArgsConstructor
public class SocialLinksAdvice {

    private final SocialLinkQueryService socialLinks;

    @ModelAttribute("socialLinks")
    public List<SocialLinkView> socialLinks() {
        return socialLinks.links();
    }
}
