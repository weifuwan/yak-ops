package io.yak.ops.sync.runtime.solution;

import io.yak.ops.connector.base.factory.FactoryUtil;
import java.util.List;
import java.util.Set;

/**
 * Enforces product solution capabilities after datasource types are known.
 *
 * <p>SolutionCatalog only lists choices; this class checks selected combinations and
 * static endpoint constraints. Physical table and resource validation belongs to JobPlanner.
 */
public final class SolutionValidator {
    private static final Set<String> JDBC_TYPES = Set.of("MYSQL", "POSTGRE_SQL", "ORACLE");
    private static final Set<String> SCHEMA_TYPES = Set.of("MYSQL", "POSTGRE_SQL");

    private SolutionValidator() {}

    /**
     * Single business rule entry for publication and physical admission.
     * Connector factories still own their own option and schema constraints.
     */
    public static void validateSelection(
            FactoryUtil factories,
            SolutionType type,
            String sourceType,
            String targetType,
            List<Step> steps,
            boolean schemaChangesEnabled,
            String writeMode) {
        validate(type, sourceType, targetType);
        validateSchemaChanges(type, sourceType, targetType, schemaChangesEnabled);
        validateSteps(sourceType, targetType, steps);
        if (type.supportsScheduling()
                && steps.contains(Step.INCREMENTAL)
                && (!SCHEMA_TYPES.contains(sourceType) || !SCHEMA_TYPES.contains(targetType))) {
            throw new IllegalArgumentException("Offline incremental is limited to MySQL and PostgreSQL");
        }
        validateCombination(factories, type, sourceType, targetType, steps, writeMode);
    }

    /** Validates one selected step/write combination against the catalog's advertised choices. */
    public static void validateCombination(
            FactoryUtil factories,
            SolutionType type,
            String sourceType,
            String targetType,
            List<Step> steps,
            String writeMode) {
        Solution selected = new SolutionCatalog(factories)
                .list(sourceType, targetType).stream()
                        .filter(choice -> choice.solutionType() == type)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Unsupported synchronization solution"));
        if (selected.allowedCombinations().stream()
                .noneMatch(choice ->
                        choice.steps().equals(steps) && choice.writeModes().contains(writeMode))) {
            throw new IllegalArgumentException("Unsupported synchronization steps or target write mode");
        }
    }

    /**
     * Validates resolved datasource types for the selected solution without connecting.
     *
     * @param type selected product solution
     * @param sourceType resolved source datasource type
     * @param targetType resolved target datasource type
     */
    public static void validate(SolutionType type, String sourceType, String targetType) {
        validateTypes(sourceType, targetType);
        if (type == SolutionType.DATABASE_REALTIME && "ORACLE".equals(targetType)) {
            throw new IllegalArgumentException(
                    "Database realtime requires a target with a compatible BIGINT snapshot key");
        }
        if (!type.supportsScheduling() && !"MYSQL".equals(sourceType)) {
            throw new IllegalArgumentException("Realtime synchronization requires a MySQL source");
        }
    }

    /** Runtime DDL currently supports MySQL and PostgreSQL metadata appliers only. */
    public static void validateSchemaChanges(
            SolutionType solution, String sourceType, String targetType, boolean enabled) {
        if (!enabled) return;
        if (solution.supportsScheduling() || !"MYSQL".equals(sourceType) || !SCHEMA_TYPES.contains(targetType)) {
            throw new IllegalArgumentException(
                    "Runtime schema changes require MySQL source and MySQL or PostgreSQL target");
        }
    }

    static void validateTypes(String sourceType, String targetType) {
        if (sourceType == null
                || targetType == null
                || !JDBC_TYPES.contains(sourceType)
                || !JDBC_TYPES.contains(targetType)) {
            throw new IllegalArgumentException("Unknown synchronization datasource type");
        }
    }

    /**
     * Checks whether both endpoints support initial schema preparation.
     *
     * @param sourceType resolved source datasource type
     * @param targetType resolved target datasource type
     * @param steps validated requested steps
     */
    public static void validateSteps(String sourceType, String targetType, List<Step> steps) {
        if (steps.contains(Step.SCHEMA) && (!SCHEMA_TYPES.contains(sourceType) || !SCHEMA_TYPES.contains(targetType))) {
            throw new IllegalArgumentException("Schema preparation requires MySQL or PostgreSQL endpoints");
        }
    }
}
