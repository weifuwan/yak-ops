package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.UserWithWorkflowDefinitionCode;

import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface UserMapper extends BaseMapper<User> {

    /**
     * select by user id
     */
    User selectById(int id);

    /**
     * delete by id
     */
    int deleteById(int id);

    /**
     * update
     */
    int updateById(@Param("et") User user);

    /**
     * query all general user
     *
     * @return user list
     */
    List<User> queryAllGeneralUser();

    /**
     * query user by name
     *
     * @param userName userName
     * @return user
     */
    User queryByUserNameAccurately(@Param("userName") String userName);

    /**
     * query user by userName and password
     *
     * @param userName userName
     * @param password password
     * @return user
     */
    User queryUserByNamePassword(@Param("userName") String userName, @Param("password") String password);

    /**
     * user page
     *
     * @param page     page
     * @param userName userName
     * @return user IPage
     */
    IPage<User> queryUserPaging(Page page,
                                @Param("userName") String userName);

    /**
     * query user detail by id
     *
     * @param userId userId
     * @return user
     */
    User queryDetailsById(@Param("userId") int userId);

    /**
     * query user list by tenantId
     *
     * @param tenantId tenantId
     * @return user list
     */
    List<User> queryUserListByTenant(@Param("tenantId") int tenantId);

    /**
     * query user by userId
     *
     * @param userId userId
     * @return user
     */
    User queryTenantCodeByUserId(@Param("userId") int userId);

    /**
     * query user by token
     *
     * @param token token
     * @param now   now date
     * @return user
     */
    User queryUserByToken(@Param("token") String token, @Param("now") Date now);

    /**
     * query user by queue name
     *
     * @param queueName queue name
     * @return user list
     */
    List<User> queryUserListByQueue(@Param("queue") String queueName);

    /**
     * check the user exist
     *
     * @param queue queue name
     * @return true if exist else return null
     */
    Boolean existUser(@Param("queue") String queue);

    /**
     * update user with old queue
     *
     * @param oldQueue old queue name
     * @param newQueue new queue name
     * @return update rows
     */
    Integer updateUserQueue(@Param("oldQueue") String oldQueue, @Param("newQueue") String newQueue);

    /**
     * query user by ids
     *
     * @param ids id list
     * @return user list
     */
    List<User> selectByIds(@Param("ids") List<Integer> ids);

    /**
     * query authed user list by projectId
     *
     * @param projectId projectId
     * @return user list
     */
    List<User> queryAuthedUserListByProjectId(@Param("projectId") int projectId);

    /**
     * query enabled users
     * @return
     */
    List<User> queryEnabledUsers();

    /**
     * query User and task flow binding relationship
     *
     * @param workflowDefinitionCodes workflowDefinitionCodes
     * @return user with process definition code
     */
    List<UserWithWorkflowDefinitionCode> queryUserWithWorkflowDefinitionCode(@Param("workflowDefinitionCodes") List<Long> workflowDefinitionCodes);

}
