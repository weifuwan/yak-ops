package io.yak.ops.plugin.task.datasync.solution;

import java.util.List;

/** Product identity and the supported data steps of a single-table synchronization. */
public enum SolutionType {
    SINGLE_TABLE_OFFLINE("单表离线", "JDBC", List.of(Step.FULL), true),
    DATABASE_OFFLINE("整库离线", "JDBC", List.of(Step.FULL), true),
    SINGLE_TABLE_REALTIME("单表实时", "MYSQL_CDC", List.of(Step.FULL, Step.INCREMENTAL), false);

    private final String displayName;
    private final String sourceIdentifier;
    private final List<Step> steps;
    private final boolean supportsScheduling;

    SolutionType(String displayName, String sourceIdentifier, List<Step> steps, boolean supportsScheduling) {
        this.displayName = displayName;
        this.sourceIdentifier = sourceIdentifier;
        this.steps = List.copyOf(steps);
        this.supportsScheduling = supportsScheduling;
    }

    /** Returns the localized product choice. */
    public String displayName() {
        return displayName;
    }

    /** Returns the Source factory that implements this solution. */
    public String sourceIdentifier() {
        return sourceIdentifier;
    }

    /** Returns the only data-step combination currently implemented for this solution. */
    public List<Step> steps() {
        return steps;
    }

    /** Whether this finite execution can be submitted by a periodic schedule. */
    public boolean supportsScheduling() {
        return supportsScheduling;
    }
}
