package io.yak.ops.flow.runtime.checkpoint;

import io.yak.ops.flow.runtime.jobgraph.JobGraph;
import io.yak.ops.flow.runtime.jobgraph.JobVertex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Checkpoint participants for a consistent local Execution cut.
 *
 * <p>Like Flink's checkpoint plan, finished vertices do not receive barriers or acknowledgments.
 * The first running vertex is the trigger root. Partially finished vertices are not yet
 * restorable by YakFlow and must decline rather than lose subtask state.
 */
public final class CheckpointPlan {

    private final Set<String> fullyFinishedOperators;
    private final List<CheckpointSnapshot.OperatorSubtask> triggerTasks;
    private final Set<CheckpointSnapshot.OperatorSubtask> tasksToAcknowledge;

    private CheckpointPlan(
            Set<String> fullyFinishedOperators,
            List<CheckpointSnapshot.OperatorSubtask> triggerTasks,
            Set<CheckpointSnapshot.OperatorSubtask> tasksToAcknowledge) {
        this.fullyFinishedOperators = Set.copyOf(fullyFinishedOperators);
        this.triggerTasks = List.copyOf(triggerTasks);
        this.tasksToAcknowledge = Set.copyOf(tasksToAcknowledge);
    }

    public Set<String> fullyFinishedOperators() {
        return fullyFinishedOperators;
    }

    public List<CheckpointSnapshot.OperatorSubtask> triggerTasks() {
        return triggerTasks;
    }

    public Set<CheckpointSnapshot.OperatorSubtask> tasksToAcknowledge() {
        return tasksToAcknowledge;
    }

    /** Creates the plan from physical Task completion facts, never from a Reader's END_OF_INPUT. */
    public static CheckpointPlan calculate(
            long checkpointId, JobGraph graph, Set<CheckpointSnapshot.OperatorSubtask> physicallyFinished)
            throws CheckpointDeclinedException {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(physicallyFinished, "physicallyFinished");
        if (graph.isSingleChainedVertex()) {
            throw new IllegalArgumentException("Checkpointing chained operators is not supported");
        }
        Set<CheckpointSnapshot.OperatorSubtask> known = new HashSet<>();
        Set<String> fullyFinished = new HashSet<>();
        Set<CheckpointSnapshot.OperatorSubtask> waitFor = new HashSet<>();
        List<CheckpointSnapshot.OperatorSubtask> roots = new ArrayList<>();
        boolean rootSelected = false;
        for (JobVertex vertex : graph.getVertices()) {
            String uid = Objects.requireNonNull(vertex.getHeadOperator().getUid(), "Stable operator UID");
            List<CheckpointSnapshot.OperatorSubtask> subtasks = new ArrayList<>();
            int finishedCount = 0;
            for (int index = 0; index < vertex.getParallelism(); index++) {
                CheckpointSnapshot.OperatorSubtask subtask = new CheckpointSnapshot.OperatorSubtask(uid, index);
                if (!known.add(subtask)) {
                    throw new IllegalArgumentException("Duplicate checkpoint subtask: " + subtask);
                }
                subtasks.add(subtask);
                if (physicallyFinished.contains(subtask)) {
                    finishedCount++;
                }
            }
            if (finishedCount > 0 && finishedCount < vertex.getParallelism()) {
                throw new CheckpointDeclinedException(
                        checkpointId, "Partially finished vertex is not safely restorable: " + uid);
            }
            if (finishedCount == vertex.getParallelism()) {
                if (rootSelected) {
                    throw new CheckpointDeclinedException(
                            checkpointId, "Downstream vertex finished before its running upstream: " + uid);
                }
                fullyFinished.add(uid);
                continue;
            }
            if (!rootSelected) {
                roots.addAll(subtasks);
                rootSelected = true;
            }
            waitFor.addAll(subtasks);
        }
        if (!known.containsAll(physicallyFinished)) {
            throw new IllegalArgumentException("Finished checkpoint task does not belong to the JobGraph");
        }
        if (roots.isEmpty()) {
            throw new CheckpointDeclinedException(checkpointId, "All checkpoint participants have finished");
        }
        return new CheckpointPlan(fullyFinished, roots, waitFor);
    }

    /**
     * Records operator finish reports produced by barrier ACKs on the Task mailbox.
     *
     * <p>A Task finishing after its barrier ACK cannot retroactively change that checkpoint.
     * Partial logical finishes remain unsupported until their state redistribution is defined.
     */
    public Set<CheckpointSnapshot.OperatorSubtask> finishedSubtasks(
            long checkpointId,
            Set<CheckpointSnapshot.OperatorSubtask> finishedReports,
            Map<CheckpointSnapshot.OperatorSubtask, Map<String, CheckpointSnapshot.SerializedState>> operatorStates)
            throws CheckpointDeclinedException {
        Objects.requireNonNull(finishedReports, "finishedReports");
        Objects.requireNonNull(operatorStates, "operatorStates");
        if (!tasksToAcknowledge.containsAll(finishedReports)) {
            throw new IllegalArgumentException("Unplanned finished operator checkpoint acknowledgment");
        }
        Map<String, Integer> total = new HashMap<>();
        Map<String, Integer> finished = new HashMap<>();
        for (CheckpointSnapshot.OperatorSubtask task : tasksToAcknowledge) {
            total.merge(task.uid(), 1, Integer::sum);
        }
        for (CheckpointSnapshot.OperatorSubtask task : finishedReports) {
            if (operatorStates.containsKey(task)) {
                throw new IllegalArgumentException("Finished task cannot also report operator state");
            }
            finished.merge(task.uid(), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> count : finished.entrySet()) {
            if (!count.getValue().equals(total.get(count.getKey()))) {
                throw new CheckpointDeclinedException(
                        checkpointId,
                        "Partially finished operator snapshot is not safely restorable: " + count.getKey());
            }
        }
        return Set.copyOf(finishedReports);
    }
}
