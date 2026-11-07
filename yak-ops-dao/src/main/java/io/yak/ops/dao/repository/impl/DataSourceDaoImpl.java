package io.yak.ops.dao.repository.impl;

import io.yak.ops.dao.entity.DataSource;
import io.yak.ops.dao.mapper.DataSourceMapper;
import io.yak.ops.dao.repository.BaseDao;
import io.yak.ops.dao.repository.DataSourceDao;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.NonNull;

@Repository
public class DataSourceDaoImpl extends BaseDao<DataSource, DataSourceMapper> implements DataSourceDao {

    public DataSourceDaoImpl(@NonNull DataSourceMapper dataSourceMapper) {
        super(dataSourceMapper);
    }

    @Override
    public DataSource queryByIdAndProjectCode(int id, long projectCode) {
        return mybatisMapper.queryByIdAndProjectCode(id, projectCode);
    }

    @Override
    public IPage<DataSource> queryByProjectCode(Page<DataSource> page, long projectCode, String name, Integer type) {
        return mybatisMapper.queryByProjectCode(page, projectCode, name, type);
    }

    @Override
    public List<DataSource> queryByProjectCodeAndType(long projectCode, Integer type) {
        return mybatisMapper.queryByProjectCodeAndType(projectCode, type);
    }

    @Override
    public boolean existsByProjectCodeAndName(long projectCode, String name, Integer excludedId) {
        return mybatisMapper.existsByProjectCodeAndName(projectCode, name, excludedId);
    }

    @Override
    public long countByProjectCode(long projectCode) {
        return mybatisMapper.countByProjectCode(projectCode);
    }

    @Override
    public List<DataSource> queryDataSourceByType(int userId, Integer type) {
        return mybatisMapper.queryDataSourceByType(userId, type);
    }

    @Override
    public IPage<DataSource> queryDataSourcePaging(IPage<DataSource> page, int userId, String name) {
        return mybatisMapper.selectPaging(page, userId, name);
    }

    @Override
    public List<DataSource> queryDataSourceByName(String name) {
        return mybatisMapper.queryDataSourceByName(name);
    }

    @Override
    public List<DataSource> queryAuthedDatasource(int userId) {
        return mybatisMapper.queryAuthedDatasource(userId);
    }

    @Override
    public List<DataSource> queryDatasourceExceptUserId(int userId) {
        return mybatisMapper.queryDatasourceExceptUserId(userId);
    }

    @Override
    public <T> List<DataSource> listAuthorizedDataSource(int userId, T[] dataSourceIds) {
        return mybatisMapper.listAuthorizedDataSource(userId, dataSourceIds);
    }

    @Override
    public IPage<DataSource> queryDataSourcePagingByIds(Page<DataSource> dataSourcePage,
                                                        List<Integer> dataSourceIds,
                                                        String name) {
        return mybatisMapper.selectPagingByIds(dataSourcePage, dataSourceIds, name);
    }

    @Override
    public List<DataSource> queryByUserId(int userId) {
        return mybatisMapper.selectByMap(Collections.singletonMap("user_id", userId));
    }
}
