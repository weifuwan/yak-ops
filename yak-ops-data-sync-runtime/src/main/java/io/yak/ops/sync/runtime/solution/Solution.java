package io.yak.ops.sync.runtime.solution;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Non-secret creation choices for one implemented directional datasource combination. */
public final class Solution {
    private final SolutionType solutionType;
    private final String displayName;
    private final List<Step> steps;
    private final List<Combination> allowedCombinations;
    private final List<Step> defaultSteps;

    @JsonCreator
    public Solution(
            @JsonProperty("solutionType") SolutionType solutionType,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("steps") List<Step> steps,
            @JsonProperty("allowedCombinations") List<Combination> allowedCombinations,
            @JsonProperty("defaultSteps") List<Step> defaultSteps) {
        steps = List.copyOf(steps);
        allowedCombinations = List.copyOf(allowedCombinations);
        defaultSteps = List.copyOf(defaultSteps);
        this.solutionType = solutionType;
        this.displayName = displayName;
        this.steps = steps;
        this.allowedCombinations = allowedCombinations;
        this.defaultSteps = defaultSteps;
    }

    @JsonProperty("solutionType")
    public SolutionType solutionType() {
        return solutionType;
    }

    @JsonProperty("displayName")
    public String displayName() {
        return displayName;
    }

    @JsonProperty("steps")
    public List<Step> steps() {
        return steps;
    }

    @JsonProperty("allowedCombinations")
    public List<Combination> allowedCombinations() {
        return allowedCombinations;
    }

    @JsonProperty("defaultSteps")
    public List<Step> defaultSteps() {
        return defaultSteps;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof Solution other)) return false;
        return java.util.Objects.equals(solutionType, other.solutionType)
                && java.util.Objects.equals(displayName, other.displayName)
                && java.util.Objects.equals(steps, other.steps)
                && java.util.Objects.equals(allowedCombinations, other.allowedCombinations)
                && java.util.Objects.equals(defaultSteps, other.defaultSteps);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(solutionType, displayName, steps, allowedCombinations, defaultSteps);
    }

    /** A valid step set and its task-level scheduling and target-writing choices. */
    public static final class Combination {
        private final List<Step> steps;
        private final boolean supportsScheduling;
        private final List<String> writeModes;

        @JsonCreator
        public Combination(
                @JsonProperty("steps") List<Step> steps,
                @JsonProperty("supportsScheduling") boolean supportsScheduling,
                @JsonProperty("writeModes") List<String> writeModes) {
            steps = List.copyOf(steps);
            writeModes = List.copyOf(writeModes);
            this.steps = steps;
            this.supportsScheduling = supportsScheduling;
            this.writeModes = writeModes;
        }

        @JsonProperty("steps")
        public List<Step> steps() {
            return steps;
        }

        @JsonProperty("supportsScheduling")
        public boolean supportsScheduling() {
            return supportsScheduling;
        }

        @JsonProperty("writeModes")
        public List<String> writeModes() {
            return writeModes;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Combination other)) return false;
            return java.util.Objects.equals(steps, other.steps)
                    && java.util.Objects.equals(supportsScheduling, other.supportsScheduling)
                    && java.util.Objects.equals(writeModes, other.writeModes);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(steps, supportsScheduling, writeModes);
        }
    }
}
