package hu.deposoft.webshop.config;

import hu.deposoft.webshop.application.page.PageImportReport;
import hu.deposoft.webshop.application.page.PageImporter;
import hu.deposoft.webshop.integrations.wordpress.SourcePages;
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
 * Manual content-page import: run with the {@code import-pages} profile and
 * {@code --webshop.import.pages-file=<pages.json>} to load the snapshot, then exit.
 * Mirrors {@code BlogImportRunner}. Live DB operations are human-only.
 */
@Configuration
@Profile("import-pages")
public class PageImportRunner {

    private static final Logger log = LoggerFactory.getLogger(PageImportRunner.class);

    @Bean
    CommandLineRunner importPages(PageImporter importer, ObjectMapper objectMapper,
                                  Environment env, ApplicationContext ctx) {
        return args -> {
            String file = env.getRequiredProperty("webshop.import.pages-file");
            log.info("Importing pages from {}", file);
            SourcePages source = objectMapper.readValue(Path.of(file).toFile(), SourcePages.class);
            PageImportReport report = importer.run(source);
            log.info("Page import report: {}", report);
            report.errors().forEach(e -> log.warn("Page import error: {}", e));
            System.exit(SpringApplication.exit(ctx, () -> report.errors().isEmpty() ? 0 : 1));
        };
    }
}
