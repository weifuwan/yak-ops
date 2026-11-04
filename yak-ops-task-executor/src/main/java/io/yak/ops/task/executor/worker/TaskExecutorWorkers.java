package io.yak.ops.task.executor.worker;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TaskExecutorWorkers {

    private final List<TaskExecutorWorker> taskExecutorWorkers;

    private final LongAdder longAdder = new LongAdder();

    public TaskExecutorWorkers(final int workerSize) {
        this.taskExecutorWorkers = IntStream
                .range(0, workerSize)
                .mapToObj(TaskExecutorWorker::new)
                .collect(Collectors.toList());
    }

    public List<TaskExecutorWorker> getWorkers() {
        return taskExecutorWorkers;
    }

    public TaskExecutorWorker getWorkerById(final int workerId) {
        return taskExecutorWorkers.get(workerId);
    }

    public TaskExecutorWorker roundRobinSelectWorker() {
        int workerIndex = (int) (longAdder.longValue() % taskExecutorWorkers.size());
        longAdder.increment();
        return taskExecutorWorkers.get(workerIndex);
    }

    public Optional<TaskExecutorWorker> selectIdleWorker() {
        for (TaskExecutorWorker taskExecutorWorker : taskExecutorWorkers) {
            if (taskExecutorWorker.getRegisteredTaskExecutorSize() == 0) {
                return Optional.of(taskExecutorWorker);
            }
        }
        return Optional.empty();
    }
}
