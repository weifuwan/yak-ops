package io.yak.framework.security.dao;

import io.yak.framework.security.common.dto.user.UserProjectDTO;
import io.yak.framework.security.common.entity.UserProject;
import io.yak.framework.security.common.po.UserProjectPO;

import java.util.List;

/**
 * 用户项目关系数据访问接口。
 */
public interface UserProjectDao {
    List<Long> selectUserIdListByProjectId(Long projectId, int userType);

    List<Long> selectProjectIdListByUserIdList(List<Long> var1);

    List<UserProjectPO> selectProjectListByUserIdList(List<Long> var1);

    void insertBatch(List<UserProject> var1);

    int deleteUserProject(List<UserProject> var1);

    void deleteByProjectId(Long projectId);

    void deleteByUserId(Long userId);

    void deleteByProjectIdAndUserType(Long projectId, int userType);

    List<UserProject> selectByProjectIds(List<Long> var1);

    List<UserProject> select(UserProjectDTO var1);
}
