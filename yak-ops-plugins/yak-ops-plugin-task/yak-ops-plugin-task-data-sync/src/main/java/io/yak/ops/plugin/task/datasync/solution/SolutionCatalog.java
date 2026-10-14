package io.yak.ops.plugin.task.datasync.solution;

import io.yak.ops.connector.base.factory.FactoryUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Enumerates implemented product solutions without opening datasource connections. */
public final class SolutionCatalog {
    private static final Set<String> JDBC_TYPES = Set.of("MYSQL", "POSTGRE_SQL", "ORACLE");
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
        choices.add(solution(SolutionType.SINGLE_TABLE_OFFLINE));
        if ("MYSQL".equals(sourceType)) {
            choices.add(solution(SolutionType.SINGLE_TABLE_REALTIME));
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
        if (type == SolutionType.SINGLE_TABLE_REALTIME && !"MYSQL".equals(sourceType)) {
            throw new IllegalArgumentException("Realtime synchronization requires a MySQL source");
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

    private Solution solution(SolutionType type) {
        factories.source(type.sourceIdentifier());
        List<String> writes = List.of("UPSERT");
        if (type == SolutionType.SINGLE_TABLE_OFFLINE) {
            writes = List.of("APPEND", "UPSERT");
        }
        return new Solution(
                type,
                type.displayName(),
                type.steps(),
                List.of(new Solution.Combination(type.steps(), type.supportsScheduling(), writes)),
                type.steps());
    }
}
