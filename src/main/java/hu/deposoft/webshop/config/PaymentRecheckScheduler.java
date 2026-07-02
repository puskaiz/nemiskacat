package hu.deposoft.webshop.config;

import hu.deposoft.webshop.application.payment.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Missing-callback safety net (T10): every 5 minutes re-checks payments stuck in
 * INITIATED for over 10 minutes via the gateway status query. The result flows
 * through the same idempotent handler as the bank callbacks.
 */
@Configuration
@EnableScheduling
@Slf4j
@RequiredArgsConstructor
public class PaymentRecheckScheduler {

    private static final Duration MIN_AGE = Duration.ofMinutes(10);

    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT2M")
    public void recheckPendingPayments() {
        int checked = paymentService.recheckPending(MIN_AGE);
        if (checked > 0) {
            log.info("Re-checked {} pending payment(s)", checked);
        }
    }
}
