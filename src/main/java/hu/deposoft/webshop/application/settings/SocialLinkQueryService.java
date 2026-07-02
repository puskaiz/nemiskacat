package hu.deposoft.webshop.application.settings;

import hu.deposoft.webshop.domain.settings.SocialLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SocialLinkQueryService {

    private final SocialLinkRepository links;

    public record SocialLinkView(String network, String url) {}

    public List<SocialLinkView> links() {
        return links.findAllByOrderByDisplayOrderAsc().stream()
                .map(s -> new SocialLinkView(s.getNetwork(), s.getUrl()))
                .toList();
    }
}
