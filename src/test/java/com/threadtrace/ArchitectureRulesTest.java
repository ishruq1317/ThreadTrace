package com.threadtrace;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureRulesTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.threadtrace");

    @Test
    void modulesShouldBeFreeOfCycles() {
        SlicesRuleDefinition.slices()
                .matching("com.threadtrace.(*)..")
                .should().beFreeOfCycles()
                .check(classes);
    }

    @Test
    void sharedAndConfigShouldNotDependOnDomainModules() {
        noClasses().that().resideInAnyPackage("..shared..", "..config..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..parties..", "..lineage..", "..evidence..",
                        "..compliance..", "..footprint..", "..passport..", "..ingestion..")
                .check(classes);
    }

    @Test
    void partiesShouldOnlyDependOnSharedAndConfig() {
        noClasses().that().resideInAPackage("..parties..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..lineage..", "..evidence..", "..compliance..",
                        "..footprint..", "..passport..", "..ingestion..")
                .check(classes);
    }

    @Test
    void lineageShouldNotDependOnDownstreamModules() {
        noClasses().that().resideInAPackage("..lineage..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..evidence..", "..compliance..", "..footprint..",
                        "..passport..", "..ingestion..")
                .check(classes);
    }

    @Test
    void restControllersShouldBeNamedController() {
        ArchRule rule = classes().that().areAnnotatedWith(RestController.class)
                .should().haveSimpleNameEndingWith("Controller");
        rule.check(classes);
    }
}
