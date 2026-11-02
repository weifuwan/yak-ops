package io.yak.ops.boot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** Asserts surviving product boundaries after the embedded engine reset. */
class BackendArchitectureTest {
    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.yak.ops");

    @Test
    void deletedEnginePackagesStayAbsent() {
        for (String prefix : new String[] {
                "io.yak.ops.core.", "io.yak.ops.cli.", "io.yak.ops.composer.",
                "io.yak.ops.flow.", "io.yak.ops.connector."
        }) {
            assertTrue(PRODUCTION.stream().noneMatch(type -> type.getPackageName().startsWith(prefix)), prefix);
        }
    }

    @Test
    void businessCannotDependOnBootOrTaskExecution() {
        noClasses().that().resideInAPackage("io.yak.ops.business..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("io.yak.ops.boot..", "io.yak.ops.task.execution..")
                .check(PRODUCTION);
    }

    @Test
    void taskExecutionCannotDependOnBoot() {
        noClasses().that().resideInAPackage("io.yak.ops.task.execution..")
                .should().dependOnClassesThat().resideInAPackage("io.yak.ops.boot..")
                .check(PRODUCTION);
    }

    @Test
    void datasourceBusinessDoesNotDependOnTaskPlugins() {
        noClasses().that().resideInAPackage("io.yak.ops.business.datasource..")
                .should().dependOnClassesThat().resideInAPackage("io.yak.ops.plugin.task..")
                .check(PRODUCTION);
    }

    @Test
    void quartzStaysInsideSchedulerPlugin() {
        noClasses().that().resideOutsideOfPackage("io.yak.ops.plugin.scheduler.quartz..")
                .should().dependOnClassesThat().resideInAPackage("org.quartz..")
                .check(PRODUCTION);
    }
}
