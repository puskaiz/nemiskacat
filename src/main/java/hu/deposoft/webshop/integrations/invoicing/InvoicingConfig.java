package hu.deposoft.webshop.integrations.invoicing;

import hu.deposoft.billingo.client.DocumentClient;
import hu.deposoft.webshop.application.invoicing.InvoiceIssuer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the invoice issuer at startup (T24): the real Billingo adapter when
 * {@code webshop.invoicing.billingo-enabled=true}, otherwise a disabled stand-in
 * that records the source FAILED for later retry. This keeps the app bootable
 * (and tests green) without Billingo credentials.
 */
@Configuration
public class InvoicingConfig {

    @Bean
    @ConditionalOnProperty(name = "webshop.invoicing.billingo-enabled", havingValue = "true")
    InvoiceIssuer billingoInvoiceIssuer(
            DocumentClient documentClient,
            @org.springframework.beans.factory.annotation.Value("${webshop.invoicing.billingo-block-id:0}") long blockId) {
        return new BillingoInvoiceIssuer(documentClient, blockId);
    }

    @Bean
    @ConditionalOnProperty(name = "webshop.invoicing.billingo-enabled", havingValue = "false", matchIfMissing = true)
    InvoiceIssuer disabledInvoiceIssuer() {
        return new DisabledInvoiceIssuer();
    }

    @Bean
    @ConditionalOnProperty(name = "webshop.invoicing.billingo-enabled", havingValue = "true")
    hu.deposoft.webshop.application.invoicing.CreditNoteIssuer billingoCreditNoteIssuer(DocumentClient documentClient) {
        return new BillingoCreditNoteIssuer(documentClient);
    }

    @Bean
    @ConditionalOnProperty(name = "webshop.invoicing.billingo-enabled", havingValue = "false", matchIfMissing = true)
    hu.deposoft.webshop.application.invoicing.CreditNoteIssuer disabledCreditNoteIssuer() {
        return new DisabledCreditNoteIssuer();
    }
}
