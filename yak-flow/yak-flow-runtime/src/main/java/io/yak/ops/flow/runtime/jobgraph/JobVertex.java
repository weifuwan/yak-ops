package io.yak.ops.flow.runtime.jobgraph;

import io.yak.ops.flow.runtime.graph.StreamNode;
import java.util.List;
import java.util.Objects;

/**
 * A deployable physical vertex. The operator chain is a single task when chaining is enabled;
 * otherwise each JobVertex contains exactly one StreamNode.
 */
public final class JobVertex {

    private final int id;
    private final List<StreamNode> operators;
    private final int parallelism;
    private final int maxParallelism;

    public JobVertex(List<StreamNode> operators) {
        this(operators, 128);
    }

    public JobVertex(List<StreamNode> operators, int maxParallelism) {
        this.operators = List.copyOf(Objects.requireNonNull(operators, "operators"));
        if (operators.isEmpty()) {
            throw new IllegalArgumentException("JobVertex requires at least one operator");
        }
        this.id = this.operators.getFirst().getId();
        this.parallelism = this.operators.getFirst().getParallelism();
        if (maxParallelism < parallelism || maxParallelism > 32768) {
            throw new IllegalArgumentException("JobVertex KeyGroup maxParallelism is out of range");
        }
        this.maxParallelism = maxParallelism;
        for (StreamNode node : this.operators) {
            if (node.getParallelism() != parallelism || (this.operators.size() > 1 && parallelism != 1)) {
                throw new IllegalArgumentException("Operators in a chain must have parallelism one");
            }
        }
        for (int i = 1; i < this.operators.size(); i++) {
            if (!this.operators.get(i - 1).isSource()
                    && !this.operators.get(i - 1).isOperator()) {
                throw new IllegalArgumentException("Only Source / OneInput operators can have a chained successor");
            }
        }
    }

    public int getId() {
        return id;
    }

    public int getParallelism() {
        return parallelism;
    }

    public int getMaxParallelism() {
        return maxParallelism;
    }

    public List<StreamNode> getOperators() {
        return operators;
    }

    public StreamNode getHeadOperator() {
        return operators.getFirst();
    }

    public StreamNode getTailOperator() {
        return operators.getLast();
    }

    public boolean isChained() {
        return operators.size() > 1;
    }
}
