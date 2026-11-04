package io.yak.ops.api.service;

import io.yak.ops.common.model.Server;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.plugin.api.monitor.DatabaseMetrics;
import io.yak.ops.extract.master.dto.WorkflowExecutorDTO;
import io.yak.ops.registry.api.enums.RegistryNodeType;
import io.yak.ops.task.executor.dto.TaskExecutorDTO;

import java.util.List;

public interface MonitorService {

    /**
     * query database state
     *
     * @param loginUser login user
     * @return data base state
     */
    List<DatabaseMetrics> queryDatabaseState(User loginUser);

    /**
     * query server list
     *
     * @param nodeType RegistryNodeType
     * @return server information list
     */
    List<Server> listServer(RegistryNodeType nodeType);

    List<WorkflowExecutorDTO> queryWorkflowExecutors(User loginUser, String masterAddress);

    List<TaskExecutorDTO> queryTaskExecutors(User loginUser, String serverAddress);
}
