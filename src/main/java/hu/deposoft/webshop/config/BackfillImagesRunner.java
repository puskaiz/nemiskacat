package hu.deposoft.webshop.config;

import hu.deposoft.webshop.application.catalog.ImageBackfillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * One-time trigger for the image backfill (see {@link ImageBackfillService}): run the app
 * with the {@code backfill-images} profile to pull already-imported catalog/workshop images
 * that still hot-link the source site into local storage, then exit. Non-destructive and
 * idempotent, so it is safe to re-run.
 */
@Configuration
@Profile("backfill-images")
public class BackfillImagesRunner {

    private static final Logger log = LoggerFactory.getLogger(BackfillImagesRunner.class);

    @Bean
    CommandLineRunner backfillImages(ImageBackfillService service, ApplicationContext ctx) {
        return args -> {
            ImageBackfillService.Report report = service.run();
            log.info("Image backfill complete: {} gallery images relocated, {} descriptions rewritten",
                    report.galleryRelocated(), report.descriptionsRewritten());
            report.errors().forEach(e -> log.warn("Backfill error: {}", e));
            System.exit(SpringApplication.exit(ctx, () -> 0));
        };
    }
}
