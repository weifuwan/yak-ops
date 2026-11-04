package io.yak.ops.api.service;

import io.yak.ops.api.utils.Result;
import io.yak.ops.dao.entity.ProjectWorkerGroup;
import io.yak.ops.dao.entity.User;

import java.util.List;

public interface ProjectWorkerGroupRelationService {

    /**
     * assign worker groups to a project
     *
     * @param loginUser the login user
     * @param projectCode the project code
     * @param workerGroups assigned worker group names
     */
    Result assignWorkerGroupsToProject(User loginUser, Long projectCode, List<String> workerGroups);

    /**
     * query worker groups that assigned to the project
     *
     * @param loginUser the login user
     * @param projectCode project code
     * @return assigned worker group relations
     */
    List<ProjectWorkerGroup> queryAssignedWorkerGroupsByProject(User loginUser, Long projectCode);

}
