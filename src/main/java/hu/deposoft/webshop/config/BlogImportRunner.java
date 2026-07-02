package hu.deposoft.webshop.config;

import hu.deposoft.webshop.application.blog.BlogImportReport;
import hu.deposoft.webshop.application.blog.BlogImporter;
import hu.deposoft.webshop.integrations.wordpress.SourceBlog;
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
 * Manual blog import trigger: run the app with the {@code import-blog} profile and
 * {@code --webshop.import.blog-file=<blog.json>} to load the blog snapshot, then exit.
 * Mirrors {@code WorkshopImportRunner}. Live DB operations are human-only.
 */
@Configuration
@Profile("import-blog")
public class BlogImportRunner {

    private static final Logger log = LoggerFactory.getLogger(BlogImportRunner.class);

    @Bean
    CommandLineRunner importBlog(BlogImporter importer, ObjectMapper objectMapper,
                                 Environment env, ApplicationContext ctx) {
        return args -> {
            String file = env.getRequiredProperty("webshop.import.blog-file");
            log.info("Importing blog from {}", file);
            SourceBlog source = objectMapper.readValue(Path.of(file).toFile(), SourceBlog.class);
            BlogImportReport report = importer.run(source);
            log.info("Blog import report: {}", report);
            report.errors().forEach(e -> log.warn("Blog import error: {}", e));
            System.exit(SpringApplication.exit(ctx, () -> report.errors().isEmpty() ? 0 : 1));
        };
    }
}
