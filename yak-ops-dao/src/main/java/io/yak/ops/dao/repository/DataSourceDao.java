package io.yak.ops.dao.repository;

import io.yak.ops.dao.entity.DataSource;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface DataSourceDao extends IDao<DataSource> {

    List<DataSource> queryDataSourceByType(int userId, Integer type);

    IPage<DataSource> queryDataSourcePaging(IPage<DataSource> page, int userId, String name);

    List<DataSource> queryDataSourceByName(String name);

    List<DataSource> queryAuthedDatasource(int userId);

    List<DataSource> queryDatasourceExceptUserId(int userId);

    <T> List<DataSource> listAuthorizedDataSource(int userId, T[] dataSourceIds);

    IPage<DataSource> queryDataSourcePagingByIds(Page<DataSource> dataSourcePage,
                                                 List<Integer> dataSourceIds,
                                                 String name);

    List<DataSource> queryByUserId(int userId);

    DataSource queryByIdAndProjectCode(int id, long projectCode);

    IPage<DataSource> queryByProjectCode(Page<DataSource> page, long projectCode, String name);

    List<DataSource> queryByProjectCodeAndType(long projectCode, Integer type);

    boolean existsByProjectCodeAndName(long projectCode, String name, Integer excludedId);

    long countByProjectCode(long projectCode);
}
