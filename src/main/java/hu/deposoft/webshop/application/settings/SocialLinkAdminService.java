package hu.deposoft.webshop.application.settings;

import hu.deposoft.webshop.application.audit.AuditService;
import hu.deposoft.webshop.domain.settings.SocialLink;
import hu.deposoft.webshop.domain.settings.SocialLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class SocialLinkAdminService {

    private final SocialLinkRepository links;
    private final AuditService audit;

    public record SocialLinkView(Long id, String network, String url, int displayOrder) {}
    public record SocialLinkUpsert(String network, String url) {}
    public record ReorderRequest(List<Long> ids) {}

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String m) { super(m); }
    }

    @Transactional(readOnly = true)
    public List<SocialLinkView> list() {
        return links.findAllByOrderByDisplayOrderAsc().stream().map(this::toView).toList();
    }

    public SocialLinkView create(SocialLinkUpsert cmd) {
        validate(cmd);
        int nextOrder = links.findAllByOrderByDisplayOrderAsc().stream()
                .mapToInt(SocialLink::getDisplayOrder).max().orElse(0) + 1;
        SocialLink saved = links.save(SocialLink.create(cmd.network().trim(), cmd.url().trim(), nextOrder));
        audit.record("SOCIAL_LINK_CREATE", "social_link", String.valueOf(saved.getId()), saved.getNetwork());
        return toView(saved);
    }

    public SocialLinkView update(Long id, SocialLinkUpsert cmd) {
        validate(cmd);
        SocialLink s = find(id);
        s.setNetwork(cmd.network().trim());
        s.setUrl(cmd.url().trim());
        audit.record("SOCIAL_LINK_UPDATE", "social_link", String.valueOf(id), s.getNetwork());
        return toView(s);
    }

    public void delete(Long id) {
        SocialLink s = find(id);
        links.delete(s);
        audit.record("SOCIAL_LINK_DELETE", "social_link", String.valueOf(id), s.getNetwork());
    }

    public List<SocialLinkView> reorder(List<Long> ids) {
        List<SocialLink> current = links.findAllByOrderByDisplayOrderAsc();
        if (ids.size() != current.size()
                || !ids.stream().sorted().toList()
                    .equals(current.stream().map(SocialLink::getId).sorted().toList())) {
            throw new IllegalArgumentException("Reorder list must contain exactly the existing social link ids");
        }
        for (int i = 0; i < ids.size(); i++) {
            Long id = ids.get(i);
            SocialLink s = current.stream().filter(x -> x.getId().equals(id)).findFirst().orElseThrow();
            s.setDisplayOrder(i + 1);
        }
        audit.record("SOCIAL_LINK_REORDER", "social_link", "*", ids.toString());
        return list();
    }

    private void validate(SocialLinkUpsert cmd) {
        if (cmd.network() == null || cmd.network().isBlank()) {
            throw new IllegalArgumentException("network must not be blank");
        }
        if (cmd.url() == null || cmd.url().isBlank()) {
            throw new IllegalArgumentException("url must not be blank");
        }
    }

    private SocialLink find(Long id) {
        return links.findById(id).orElseThrow(() -> new NotFoundException("Social link not found: " + id));
    }

    private SocialLinkView toView(SocialLink s) {
        return new SocialLinkView(s.getId(), s.getNetwork(), s.getUrl(), s.getDisplayOrder());
    }
}
