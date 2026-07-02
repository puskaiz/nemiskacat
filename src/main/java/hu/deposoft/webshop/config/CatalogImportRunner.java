package hu.deposoft.webshop.config;

import tools.jackson.databind.ObjectMapper;
import hu.deposoft.webshop.application.catalog.CatalogImporter;
import hu.deposoft.webshop.application.catalog.ImportReport;
import hu.deposoft.webshop.integrations.woo.JsonFileCatalogSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Path;

/**
 * Manual import trigger (T4): run the app with the {@code import} profile and
 * {@code --webshop.import.file=<catalog.json>} to load a catalog snapshot, then exit.
 * Scheduling can be added later once the REST source exists.
 */
@Configuration
@Profile("import")
public class CatalogImportRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogImportRunner.class);

    @Bean
    CommandLineRunner importCatalog(CatalogImporter importer, ObjectMapper objectMapper,
                                    org.springframework.core.env.Environment env,
                                    org.springframework.context.ApplicationContext ctx) {
        return args -> {
            String file = env.getRequiredProperty("webshop.import.file");
            log.info("Importing catalog from {}", file);
            ImportReport report = importer.run(new JsonFileCatalogSource(objectMapper, Path.of(file)).load());
            log.info("Import report: {}", report);
            report.errors().forEach(e -> log.warn("Import error: {}", e));
            System.exit(org.springframework.boot.SpringApplication.exit(ctx, () -> report.errors().isEmpty() ? 0 : 1));
        };
    }
}
