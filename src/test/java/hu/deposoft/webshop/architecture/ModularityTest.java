package hu.deposoft.webshop.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Enforces the modular-monolith boundaries declared in CLAUDE.md. The module is a
 * single Maven artifact, so these rules — not the build system — keep the layers
 * honest. They run in CI and fail the build on a violation.
 */
@AnalyzeClasses(packages = "hu.deposoft.webshop", importOptions = ImportOption.DoNotIncludeTests.class)
class ModularityTest {

    private static final String BASE = "hu.deposoft.webshop.";

    /**
     * Allowed dependency direction:
     * domain  <-  application  <-  {web, api}
     * web and api are adapters at the top: nothing depends on them, and they do
     * not depend on each other.
     */
    @ArchTest
    static final ArchRule layering = layeredArchitecture().consideringOnlyDependenciesInLayers()
            .withOptionalLayers(true)
            .layer("Domain").definedBy(BASE + "domain..")
            .layer("Application").definedBy(BASE + "application..")
            .layer("Web").definedBy(BASE + "web..")
            .layer("Api").definedBy(BASE + "api..")
            .layer("Integrations").definedBy(BASE + "integrations..")
            .whereLayer("Web").mayNotBeAccessedByAnyLayer()
            .whereLayer("Api").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Web", "Api", "Integrations")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Web", "Api", "Integrations");

    // Integrations may access Application: inbound adapters (payment gateway events,
    // courier callbacks) invoke application services exactly like controllers do.

    /** The domain layer stays framework-light: no dependency on the web/api adapters. */
    @ArchTest
    static final ArchRule domainIsIndependentOfAdapters = classes()
            .that().resideInAPackage(BASE + "domain..")
            .should().onlyDependOnClassesThat()
            .resideOutsideOfPackages(BASE + "web..", BASE + "api..", BASE + "integrations..");

    /** Controllers belong only to the web (Thymeleaf) and api (REST) adapter layers. */
    @ArchTest
    static final ArchRule controllersLiveInAdapterLayers = classes()
            .that().areAnnotatedWith(org.springframework.stereotype.Controller.class)
            .or().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should().resideInAnyPackage(BASE + "web..", BASE + "api..");
}
