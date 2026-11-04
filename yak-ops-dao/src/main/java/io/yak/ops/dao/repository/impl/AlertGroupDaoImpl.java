package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.AlertGroup;
import io.yak.ops.dao.mapper.AlertGroupMapper;
import io.yak.ops.dao.repository.AlertGroupDao;
import io.yak.ops.dao.repository.BaseDao;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.NonNull;

@Repository
public class AlertGroupDaoImpl extends BaseDao<AlertGroup, AlertGroupMapper> implements AlertGroupDao {

    public AlertGroupDaoImpl(@NonNull AlertGroupMapper alertGroupMapper) {
        super(alertGroupMapper);
    }

    @Override
    public IPage<AlertGroup> queryAlertGroupPage(Page<AlertGroup> page, String groupName) {
        return mybatisMapper.queryAlertGroupPage(page, groupName);
    }

    @Override
    public IPage<AlertGroup> queryAlertGroupPageByIds(Page<AlertGroup> page, List<Integer> ids, String searchVal) {
        return mybatisMapper.queryAlertGroupPageByIds(page, ids, searchVal);
    }

    @Override
    public List<AlertGroup> queryAllGroupList() {
        return mybatisMapper.queryAllGroupList();
    }

    @Override
    public List<AlertGroup> queryByUserId(int userId) {
        return mybatisMapper.queryByUserId(userId);
    }

    @Override
    public List<String> queryInstanceIdsList() {
        return mybatisMapper.queryInstanceIdsList();
    }

    @Override
    public boolean existGroupName(String groupName) {
        return Boolean.TRUE.equals(mybatisMapper.existGroupName(groupName));
    }
}
