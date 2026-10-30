package io.yak.ops.sync.runtime.solution;

import io.yak.ops.connector.base.factory.FactoryUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Enumerates implemented product solutions without opening datasource connections. */
public final class SolutionCatalog {
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
        SolutionValidator.validateTypes(sourceType, targetType);
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
        if (!type.supportsScheduling()) {
            combinations.add(new Solution.Combination(List.of(Step.FULL), false, List.of("UPSERT")));
            combinations.add(new Solution.Combination(List.of(Step.INCREMENTAL), false, List.of("UPSERT")));
        }
        boolean supportedIncremental =
                type.supportsScheduling() && SCHEMA_TYPES.contains(sourceType) && SCHEMA_TYPES.contains(targetType);
        if (supportedIncremental) {
            applicable.add(Step.INCREMENTAL);
            combinations.add(new Solution.Combination(List.of(Step.INCREMENTAL), true, List.of("UPSERT")));
            combinations.add(new Solution.Combination(List.of(Step.FULL, Step.INCREMENTAL), true, List.of("UPSERT")));
        }
        if (SCHEMA_TYPES.contains(sourceType) && SCHEMA_TYPES.contains(targetType)) {
            applicable.addFirst(Step.SCHEMA);
            combinations.add(new Solution.Combination(
                    List.of(Step.SCHEMA, Step.FULL),
                    type.supportsScheduling(),
                    type.supportsScheduling() ? writes : List.of("UPSERT")));
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
