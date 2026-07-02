package hu.deposoft.webshop.application.audit;

import hu.deposoft.webshop.domain.order.AuditEntry;
import hu.deposoft.webshop.domain.order.AuditEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records staff actions for audit (T15), tagging each with the current actor. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEntryRepository entries;

    @Transactional
    public void record(String action, String entityType, String entityId, String summary) {
        entries.save(AuditEntry.of(currentActor(), action, entityType, entityId, summary));
    }

    private String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return "system";
        }
        return auth.getName();
    }
}
