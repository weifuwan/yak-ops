package io.yak.ops.task.executor;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class TaskExecutorRepository implements ITaskExecutorRepository {

    private final Map<Integer, ITaskExecutor> taskExecutorMap = new ConcurrentHashMap<>();

    @Override
    public void put(final ITaskExecutor taskExecutor) {
        checkNotNull(taskExecutor);
        taskExecutorMap.put(taskExecutor.getId(), taskExecutor);
    }

    @Override
    public Optional<ITaskExecutor> get(final Integer taskExecutorId) {
        return Optional.ofNullable(taskExecutorMap.get(taskExecutorId));
    }

    @Override
    public Collection<ITaskExecutor> getAll() {
        return taskExecutorMap.values();
    }

    @Override
    public boolean contains(final Integer taskExecutorId) {
        return taskExecutorMap.containsKey(taskExecutorId);
    }

    @Override
    public void remove(final Integer taskExecutorId) {
        taskExecutorMap.remove(taskExecutorId);
    }

    @Override
    public void clear() {
        taskExecutorMap.clear();
    }

}
