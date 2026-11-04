package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.UserWithWorkflowDefinitionCode;
import io.yak.ops.dao.mapper.UserMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.UserDao;

import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.NonNull;

@Repository
public class UserDaoImpl extends BaseDao<User, UserMapper> implements UserDao {

    public UserDaoImpl(@NonNull UserMapper userMapper) {
        super(userMapper);
    }

    @Override
    public List<User> queryAllGeneralUser() {
        return mybatisMapper.queryAllGeneralUser();
    }

    @Override
    public User queryByUserNameAccurately(String userName) {
        return mybatisMapper.queryByUserNameAccurately(userName);
    }

    @Override
    public User queryUserByNamePassword(String userName, String password) {
        return mybatisMapper.queryUserByNamePassword(userName, password);
    }

    @Override
    public IPage<User> queryUserPaging(Page<User> page, String userName) {
        return mybatisMapper.queryUserPaging(page, userName);
    }

    @Override
    public User queryDetailsById(int userId) {
        return mybatisMapper.queryDetailsById(userId);
    }

    @Override
    public List<User> queryUserListByTenant(int tenantId) {
        return mybatisMapper.queryUserListByTenant(tenantId);
    }

    @Override
    public User queryTenantCodeByUserId(int userId) {
        return mybatisMapper.queryTenantCodeByUserId(userId);
    }

    @Override
    public User queryUserByToken(String token, Date now) {
        return mybatisMapper.queryUserByToken(token, now);
    }

    @Override
    public List<User> queryUserListByQueue(String queueName) {
        return mybatisMapper.queryUserListByQueue(queueName);
    }

    @Override
    public Boolean existUser(String queue) {
        return mybatisMapper.existUser(queue);
    }

    @Override
    public Integer updateUserQueue(String oldQueue, String newQueue) {
        return mybatisMapper.updateUserQueue(oldQueue, newQueue);
    }

    @Override
    public List<User> queryAuthedUserListByProjectId(int projectId) {
        return mybatisMapper.queryAuthedUserListByProjectId(projectId);
    }

    @Override
    public List<User> queryEnabledUsers() {
        return mybatisMapper.queryEnabledUsers();
    }

    @Override
    public List<UserWithWorkflowDefinitionCode> queryUserWithWorkflowDefinitionCode(List<Long> workflowDefinitionCodes) {
        return mybatisMapper.queryUserWithWorkflowDefinitionCode(workflowDefinitionCodes);
    }
}
