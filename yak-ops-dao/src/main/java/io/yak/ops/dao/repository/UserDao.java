package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.UserWithWorkflowDefinitionCode;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface UserDao extends IDao<User> {

    List<User> queryAllGeneralUser();

    User queryByUserNameAccurately(String userName);

    User queryUserByNamePassword(String userName, String password);

    IPage<User> queryUserPaging(Page<User> page, String userName);

    User queryDetailsById(int userId);

    List<User> queryUserListByTenant(int tenantId);

    User queryTenantCodeByUserId(int userId);

    User queryUserByToken(String token, Date now);

    List<User> queryUserListByQueue(String queueName);

    Boolean existUser(String queue);

    Integer updateUserQueue(String oldQueue, String newQueue);

    List<User> queryAuthedUserListByProjectId(int projectId);

    List<User> queryEnabledUsers();

    List<UserWithWorkflowDefinitionCode> queryUserWithWorkflowDefinitionCode(List<Long> workflowDefinitionCodes);
}
