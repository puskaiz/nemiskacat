package hu.deposoft.webshop.config;

import hu.deposoft.webshop.application.workshop.WorkshopImportReport;
import hu.deposoft.webshop.application.workshop.WorkshopImporter;
import hu.deposoft.webshop.integrations.woo.SourceWorkshops;
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
 * Manual workshop import trigger: run the app with the {@code import-workshops}
 * profile and {@code --webshop.import.workshops-file=<workshops.json>} to load the
 * workshop snapshot, then exit. Mirrors {@code CatalogImportRunner}.
 */
@Configuration
@Profile("import-workshops")
public class WorkshopImportRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkshopImportRunner.class);

    @Bean
    CommandLineRunner importWorkshops(WorkshopImporter importer, ObjectMapper objectMapper,
                                      Environment env, ApplicationContext ctx) {
        return args -> {
            String file = env.getRequiredProperty("webshop.import.workshops-file");
            log.info("Importing workshops from {}", file);
            SourceWorkshops source = objectMapper.readValue(Path.of(file).toFile(), SourceWorkshops.class);
            WorkshopImportReport report = importer.run(source);
            log.info("Workshop import report: {}", report);
            report.errors().forEach(e -> log.warn("Workshop import error: {}", e));
            System.exit(SpringApplication.exit(ctx, () -> report.errors().isEmpty() ? 0 : 1));
        };
    }
}
