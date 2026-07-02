package hu.deposoft.webshop.integrations.khpos;

import hu.deposoft.khpos.starter.autoconfig.KhposProperties;
import hu.deposoft.khpos.starter.service.KhposPaymentService;
import hu.deposoft.webshop.application.payment.PaymentGateway;
import hu.deposoft.webshop.application.payment.PaymentService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * Wires the KHPos starter behind the PaymentGateway port. Property-based
 * conditions (not bean-based): user configuration is processed before the
 * starter's auto-configuration, so @ConditionalOnBean would be unreliable here.
 */
@Configuration
public class KhposIntegrationConfig {

    @Configuration
    @ConditionalOnProperty(name = "khpos.enabled", havingValue = "true")
    static class Enabled {

        @Bean
        PaymentGateway khposGatewayAdapter(KhposPaymentService khpos, KhposProperties properties) {
            return new KhposGatewayAdapter(khpos, properties);
        }

        @Bean
        KhposEventBridge khposEventBridge(PaymentService paymentService) {
            return new KhposEventBridge(paymentService);
        }
    }

    @Bean
    @ConditionalOnProperty(name = "khpos.enabled", havingValue = "false", matchIfMissing = true)
    PaymentGateway disabledPaymentGateway() {
        return new DisabledPaymentGateway();
    }
}
