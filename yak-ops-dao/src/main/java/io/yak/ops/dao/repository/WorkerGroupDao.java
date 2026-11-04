package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.WorkerGroup;

import java.util.List;

public interface WorkerGroupDao extends IDao<WorkerGroup> {

    boolean deleteByWorkerGroupName(String workerGroupName);

    List<String> queryAllWorkerGroupNames();

    List<WorkerGroup> queryAllWorkerGroup();

    List<WorkerGroup> queryWorkerGroupByName(String name);
}
