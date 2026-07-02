package hu.deposoft.webshop.config;

import hu.deposoft.webshop.application.order.RefundReconciliationService;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refund reconciliation safety net (H3): hourly, re-checks CONFIRMED payments on still-open
 * (PAID/PACKING) orders against the gateway's refund state and heals any whose money was
 * refunded out-of-band or whose refund commit was lost. Mirrors {@link PaymentRecheckScheduler}
 * (scheduling is already enabled there). The loop lives here, in a bean SEPARATE from the
 * service, so each {@code reconcileOne} call runs in its own transaction and one failure
 * cannot abort the batch.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RefundReconciliationScheduler {

    /** Re-check each payment at most once per 24h. */
    private static final Duration MAX_AGE = Duration.ofHours(24);

    private final RefundReconciliationService reconciliation;

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    public void reconcileRefunds() {
        List<Long> ids = reconciliation.findCandidatePaymentIds(MAX_AGE);
        int healed = 0;
        int alerted = 0;
        for (Long id : ids) {
            try {
                switch (reconciliation.reconcileOne(id)) {
                    case HEALED -> healed++;
                    case ALERTED -> alerted++;
                    case NONE -> { }
                }
            } catch (RuntimeException e) {
                log.warn("Refund reconciliation failed for payment {}: {}", id, e.getMessage());
            }
        }
        if (healed > 0 || alerted > 0) {
            log.info("Refund reconciliation: {} healed, {} alerted ({} checked)", healed, alerted, ids.size());
        }
    }
}
