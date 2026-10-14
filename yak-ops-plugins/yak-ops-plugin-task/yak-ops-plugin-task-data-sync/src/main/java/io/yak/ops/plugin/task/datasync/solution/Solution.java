package io.yak.ops.plugin.task.datasync.solution;

import java.util.List;

/** Non-secret creation choices for one implemented directional datasource combination. */
public record Solution(
        SolutionType solutionType,
        String displayName,
        List<Step> steps,
        List<Combination> allowedCombinations,
        List<Step> defaultSteps) {
    public Solution {
        steps = List.copyOf(steps);
        allowedCombinations = List.copyOf(allowedCombinations);
        defaultSteps = List.copyOf(defaultSteps);
    }

    /** A valid step set and its task-level scheduling and target-writing choices. */
    public record Combination(List<Step> steps, boolean supportsScheduling, List<String> writeModes) {
        public Combination {
            steps = List.copyOf(steps);
            writeModes = List.copyOf(writeModes);
        }
    }
}
