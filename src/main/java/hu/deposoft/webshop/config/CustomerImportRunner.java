package hu.deposoft.webshop.config;

import hu.deposoft.webshop.application.customer.CustomerImporter;
import hu.deposoft.webshop.integrations.wordpress.SourceCustomer;
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
import java.util.List;

/**
 * Manual customer-migration trigger (T13): run with the {@code import-customers}
 * profile and {@code --webshop.import.customers-file=<customers.json>} (produced by
 * scripts/woo-export/export-customers.py), then exit. Idempotent.
 */
@Configuration
@Profile("import-customers")
public class CustomerImportRunner {

    private static final Logger log = LoggerFactory.getLogger(CustomerImportRunner.class);

    @Bean
    CommandLineRunner importCustomers(CustomerImporter importer, ObjectMapper objectMapper,
                                      Environment env, ApplicationContext ctx) {
        return args -> {
            String file = env.getRequiredProperty("webshop.import.customers-file");
            log.info("Importing customers from {}", file);
            SourceCustomer[] sources = objectMapper.readValue(Path.of(file).toFile(), SourceCustomer[].class);
            CustomerImporter.Report report = importer.run(List.of(sources));
            log.info("Customer import report: {}", report);
            System.exit(SpringApplication.exit(ctx, () -> 0));
        };
    }
}
