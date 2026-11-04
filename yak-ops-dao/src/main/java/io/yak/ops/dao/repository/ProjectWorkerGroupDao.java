package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.ProjectWorkerGroup;

import java.util.List;
import java.util.Set;

public interface ProjectWorkerGroupDao extends IDao<ProjectWorkerGroup> {

    boolean deleteByProjectCode(Long projectCode);

    Set<String> queryAssignedWorkerGroupNamesByProjectCode(Long projectCode);

    boolean deleteByProjectCodeAndWorkerGroups(Long projectCode, List<String> workerGroups);

    List<ProjectWorkerGroup> queryByProjectCode(Long projectCode);
}
