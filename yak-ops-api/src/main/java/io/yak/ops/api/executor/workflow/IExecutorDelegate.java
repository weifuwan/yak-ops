package io.yak.ops.api.executor.workflow;

public interface IExecutorDelegate<T, R> {

    R execute(T t);
}
