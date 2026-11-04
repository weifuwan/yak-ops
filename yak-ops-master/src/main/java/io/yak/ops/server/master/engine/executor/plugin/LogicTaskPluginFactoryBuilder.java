package io.yak.ops.server.master.engine.executor.plugin;

import io.yak.ops.plugin.task.api.parameters.AbstractParameters;
import io.yak.ops.server.master.exception.LogicTaskFactoryNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LogicTaskPluginFactoryBuilder {

    private final Map<String, ILogicTaskPluginFactory<? extends ILogicTask<? extends AbstractParameters>>> logicTaskPluginFactoryMap =
            new ConcurrentHashMap<>();

    public LogicTaskPluginFactoryBuilder(List<ILogicTaskPluginFactory<? extends ILogicTask<? extends AbstractParameters>>> logicTaskPluginFactories) {
        logicTaskPluginFactories.forEach(
                logicTaskPluginFactory -> logicTaskPluginFactoryMap.put(logicTaskPluginFactory.getTaskType(),
                        logicTaskPluginFactory));
    }

    public ILogicTaskPluginFactory<? extends ILogicTask<? extends AbstractParameters>> createILogicTaskPluginFactory(String taskType) throws LogicTaskFactoryNotFoundException {
        ILogicTaskPluginFactory<? extends ILogicTask<? extends AbstractParameters>> logicTaskPluginFactory =
                logicTaskPluginFactoryMap.get(taskType);
        if (logicTaskPluginFactory == null) {
            throw new LogicTaskFactoryNotFoundException("Cannot find the logic task factory: " + taskType);
        }
        return logicTaskPluginFactory;
    }

}
