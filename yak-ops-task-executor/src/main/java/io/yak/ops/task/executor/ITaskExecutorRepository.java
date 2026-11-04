package io.yak.ops.task.executor;

import java.util.Collection;
import java.util.Optional;

public interface ITaskExecutorRepository {

    void put(final ITaskExecutor taskExecutor);

    Optional<ITaskExecutor> get(final Integer taskExecutorId);

    Collection<ITaskExecutor> getAll();

    boolean contains(final Integer taskExecutorId);

    void remove(final Integer taskExecutorId);

    void clear();

}
