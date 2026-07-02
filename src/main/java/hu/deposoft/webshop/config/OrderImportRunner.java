package hu.deposoft.webshop.config;

import hu.deposoft.webshop.application.order.OrderImportReport;
import hu.deposoft.webshop.application.order.OrderImporter;
import hu.deposoft.webshop.integrations.woo.JsonFileOrderSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;

/**
 * Manual order-migration trigger (cutover): run with the {@code import-orders}
 * profile and {@code --webshop.import.orders-file=<orders.json>} (produced by
 * scripts/woo-export/export-orders.py), then exit. Idempotent.
 */
@Configuration
@Profile("import-orders")
public class OrderImportRunner {

    private static final Logger log = LoggerFactory.getLogger(OrderImportRunner.class);

    @Bean
    CommandLineRunner importOrders(OrderImporter importer, ObjectMapper objectMapper,
                                   Environment env, ApplicationContext ctx) {
        return args -> {
            String file = env.getRequiredProperty("webshop.import.orders-file");
            log.info("Importing orders from {}", file);
            OrderImportReport report =
                    importer.run(new JsonFileOrderSource(objectMapper, Path.of(file)).load());
            log.info("Order import report: {}", report);
            if (!report.orphanSkus().isEmpty()) {
                log.warn("Orphan SKUs (lines kept without a live variant): {}", report.orphanSkus());
            }
            if (!report.unknownStatuses().isEmpty()) {
                log.warn("Unknown Woo statuses (orders skipped): {}", report.unknownStatuses());
            }
            if (report.failedCount() > 0) {
                log.warn("Failed orders ({}) — re-run to retry (import is idempotent): {}",
                        report.failedCount(), report.failures());
            }
            System.exit(SpringApplication.exit(ctx, () -> 0));
        };
    }
}
