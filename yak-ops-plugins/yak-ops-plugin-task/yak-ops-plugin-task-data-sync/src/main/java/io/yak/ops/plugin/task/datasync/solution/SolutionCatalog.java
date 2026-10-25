package io.yak.ops.plugin.task.datasync.solution;

import io.yak.ops.connector.base.factory.FactoryUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Enumerates implemented product solutions without opening datasource connections. */
public final class SolutionCatalog {
    private static final Set<String> JDBC_TYPES = Set.of("MYSQL", "POSTGRE_SQL", "ORACLE");
    private static final Set<String> SCHEMA_TYPES = Set.of("MYSQL", "POSTGRE_SQL");
    private final FactoryUtil factories;

    public SolutionCatalog(FactoryUtil factories) {
        this.factories = Objects.requireNonNull(factories, "factories");
    }

    /**
     * Returns choices for an ordered pair of product datasource types.
     *
     * @param sourceType datasource type, not a connector identifier
     * @param targetType datasource type, not a connector identifier
     * @return immutable supported choices; no resource authorization is implied
     */
    public List<Solution> list(String sourceType, String targetType) {
        validateTypes(sourceType, targetType);
        factories.sink("JDBC");
        List<Solution> choices = new ArrayList<>();
        choices.add(solution(sourceType, targetType, SolutionType.SINGLE_TABLE_OFFLINE));
        if ("MYSQL".equals(sourceType)) {
            choices.add(solution(sourceType, targetType, SolutionType.SINGLE_TABLE_REALTIME));
        }
        choices.add(solution(sourceType, targetType, SolutionType.DATABASE_OFFLINE));
        if ("MYSQL".equals(sourceType) && !"ORACLE".equals(targetType)) {
            choices.add(solution(sourceType, targetType, SolutionType.DATABASE_REALTIME));
        }
        return List.copyOf(choices);
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

    private static void validateTypes(String sourceType, String targetType) {
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

    private Solution solution(String sourceType, String targetType, SolutionType type) {
        factories.source(type.sourceIdentifier());
        List<String> writes = List.of("UPSERT");
        if (type.supportsScheduling()) {
            writes = List.of("APPEND", "UPSERT");
        } else if (type == SolutionType.SINGLE_TABLE_REALTIME
                && "MYSQL".equals(sourceType)
                && "MYSQL".equals(targetType)) {
            writes = List.of("UPSERT", "RETRACT");
        }
        List<Step> applicable = new ArrayList<>(type.steps());
        List<Solution.Combination> combinations = new ArrayList<>();
        combinations.add(new Solution.Combination(type.steps(), type.supportsScheduling(), writes));
        boolean supportedIncremental =
                type.supportsScheduling() && SCHEMA_TYPES.contains(sourceType) && SCHEMA_TYPES.contains(targetType);
        if (supportedIncremental) {
            applicable.add(Step.INCREMENTAL);
            combinations.add(new Solution.Combination(List.of(Step.INCREMENTAL), true, List.of("UPSERT")));
            combinations.add(new Solution.Combination(List.of(Step.FULL, Step.INCREMENTAL), true, List.of("UPSERT")));
        }
        if (SCHEMA_TYPES.contains(sourceType) && SCHEMA_TYPES.contains(targetType)) {
            applicable.addFirst(Step.SCHEMA);
            combinations.add(
                    new Solution.Combination(List.of(Step.SCHEMA, Step.FULL), type.supportsScheduling(), writes));
            if (supportedIncremental) {
                combinations.add(new Solution.Combination(
                        List.of(Step.SCHEMA, Step.FULL, Step.INCREMENTAL), true, List.of("UPSERT")));
            } else if (!type.supportsScheduling()) {
                combinations.add(new Solution.Combination(applicable, false, writes));
            }
        }
        return new Solution(type, type.displayName(), applicable, combinations, type.steps());
    }
}
