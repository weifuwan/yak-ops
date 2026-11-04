package io.yak.ops.server.master.engine.executor.plugin;

public interface ITaskParameterDeserializer<T> {

    T deserialize(String taskParamsJson);

}
