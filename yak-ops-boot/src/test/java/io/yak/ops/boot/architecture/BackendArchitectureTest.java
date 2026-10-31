package io.yak.ops.boot.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import io.yak.ops.core.archfixture.IllegalCoreDependency;
import org.junit.jupiter.api.Test;

/** Checks compiled module dependencies. */
class BackendArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.yak.ops");

    private static final ArchRule CORE_ISOLATION = noClasses()
            .that()
            .resideInAPackage("io.yak.ops.core..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "io.yak.ops.flow.runtime..",
                    "io.yak.ops.cli..",
                    "io.yak.ops.composer..",
                    "io.yak.ops.connector..",
                    "io.yak.ops.business..",
                    "io.yak.ops.task.execution..",
                    "io.yak.ops.dao..",
                    "io.yak.ops.plugin..",
                    "io.yak.ops.boot..");

    @Test
    void checksAllExpectedModulePackages() {
        for (String prefix : new String[] {
            "io.yak.ops.core.",
            "io.yak.ops.cli.",
            "io.yak.ops.composer.",
            "io.yak.ops.flow.runtime.",
            "io.yak.ops.connector.",
            "io.yak.ops.business.",
            "io.yak.ops.task.execution.",
            "io.yak.ops.boot."
        }) {
            assertTrue(
                    PRODUCTION.stream().anyMatch(type -> type.getPackageName().startsWith(prefix)), prefix);
        }
    }

    @Test
    void coreCannotDependOnRuntimeConnectorsOrProductCode() {
        CORE_ISOLATION.check(PRODUCTION);
    }

    @Test
    void runtimeCannotDependOnProductCode() {
        noClasses()
                .that()
                .resideInAPackage("io.yak.ops.flow.runtime..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("io.yak.ops.business..", "io.yak.ops.dao..", "io.yak.ops.boot..", "io.yak.ops.composer..", "io.yak.ops.cli..")
                .check(PRODUCTION);
    }

    @Test
    void connectorsCannotDependOnProductCode() {
        noClasses()
                .that()
                .resideInAPackage("io.yak.ops.connector..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "io.yak.ops.business..",
                        "io.yak.ops.dao..",
                        "io.yak.ops.boot..",
                        "io.yak.ops.plugin.task..",
                        "io.yak.ops.plugin.datasource..",
                        "io.yak.ops.plugin.database..",
                        "io.yak.ops.composer..",
                        "io.yak.ops.cli..")
                .check(PRODUCTION);
    }

    @Test
    void pipelineCompositionCannotDependOnProductPersistenceOrTaskImplementation() {
        noClasses()
                .that()
                .resideInAnyPackage("io.yak.ops.cli..", "io.yak.ops.composer..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("io.yak.ops.business..", "io.yak.ops.dao..", "io.yak.ops.boot..",
                        "io.yak.ops.plugin.task.datasync..")
                .check(PRODUCTION);
        assertTrue(PRODUCTION.stream().noneMatch(type -> type.getPackageName().startsWith("io.yak.ops.sync.runtime")));
    }

    @Test
    void businessCannotDependOnBoot() {
        noClasses()
                .that()
                .resideInAPackage("io.yak.ops.business..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("io.yak.ops.boot..")
                .check(PRODUCTION);
    }

    @Test
    void taskExecutionCannotDependOnBootOrYakFlowRuntime() {
        noClasses()
                .that()
                .resideInAPackage("io.yak.ops.task.execution..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("io.yak.ops.boot..", "io.yak.ops.flow.runtime..", "io.yak.ops.connector..")
                .check(PRODUCTION);
    }

    @Test
    void businessCannotDependOnTaskExecutionCoordination() {
        noClasses()
                .that()
                .resideInAPackage("io.yak.ops.business..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("io.yak.ops.task.execution..")
                .check(PRODUCTION);
    }

    @Test
    void taskPluginsDoNotDependOnProductBusinessServices() {
        noClasses()
                .that()
                .resideInAPackage("io.yak.ops.plugin.task.datasync..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("io.yak.ops.business..", "io.yak.ops.dao..", "io.yak.ops.boot..")
                .check(PRODUCTION);
    }

    @Test
    void datasourceBusinessDoesNotDependOnTaskPlugins() {
        noClasses()
                .that()
                .resideInAPackage("io.yak.ops.business.datasource..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("io.yak.ops.plugin.task..")
                .check(PRODUCTION);
    }

    @Test
    void quartzStaysInsideItsPluginAndTaskBusinessDoesNotDependOnScheduleBusiness() {
        assertTrue(PRODUCTION.stream()
                .anyMatch(type -> type.getName().equals("io.yak.ops.plugin.scheduler.quartz.QuartzCronService")));
        noClasses()
                .that()
                .resideOutsideOfPackage("io.yak.ops.plugin.scheduler.quartz..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.quartz..")
                .check(PRODUCTION);
        noClasses()
                .that()
                .resideInAPackage("io.yak.ops.business.task..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("io.yak.ops.business.schedule..", "org.quartz..")
                .check(PRODUCTION);
    }

    @Test
    void catchesAnActualForbiddenDependency() {
        JavaClasses invalid = new ClassFileImporter().importClasses(IllegalCoreDependency.class);
        assertTrue(CORE_ISOLATION.evaluate(invalid).hasViolation());
    }
}
