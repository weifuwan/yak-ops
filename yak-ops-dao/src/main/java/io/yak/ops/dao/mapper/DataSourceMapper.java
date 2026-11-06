package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.DataSource;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * datasource mapper interface
 */
public interface DataSourceMapper extends BaseMapper<DataSource> {

    /**
     * query datasource by type
     * @param userId userId
     * @param type type
     * @return datasource list
     */
    DataSource queryByIdAndProjectCode(@Param("id") int id, @Param("projectCode") long projectCode);

    IPage<DataSource> queryByProjectCode(Page<DataSource> page,
                                         @Param("projectCode") long projectCode,
                                         @Param("name") String name,
                                         @Param("type") Integer type);

    List<DataSource> queryByProjectCodeAndType(@Param("projectCode") long projectCode,
                                               @Param("type") Integer type);

    long countByProjectCode(@Param("projectCode") long projectCode);

    boolean existsByProjectCodeAndName(@Param("projectCode") long projectCode,
                                       @Param("name") String name,
                                       @Param("excludedId") Integer excludedId);

    List<DataSource> queryDataSourceByType(@Param("userId") int userId, @Param("type") Integer type);

    /**
     * datasource page
     * @param page page
     * @param userId userId
     * @param name name
     * @return datasource IPage
     */
    IPage<DataSource> selectPaging(IPage<DataSource> page,
                                   @Param("userId") int userId,
                                   @Param("name") String name);

    /**
     * query datasource by name
     * @param name name
     * @return datasource list
     */
    List<DataSource> queryDataSourceByName(@Param("name") String name);

    /**
     * query authed datasource
     * @param userId userId
     * @return datasource list
     */
    List<DataSource> queryAuthedDatasource(@Param("userId") int userId);

    /**
     * query datasource except userId
     * @param userId userId
     * @return datasource list
     */
    List<DataSource> queryDatasourceExceptUserId(@Param("userId") int userId);

    /**
     * list all datasource by type
     * @param type datasource type
     * @return datasource list
     */
    List<DataSource> listAllDataSourceByType(@Param("type") Integer type);

    /**
     * list authorized datasource
     *
     * @param userId userId
     * @param dataSourceIds data source id array
     * @param <T> T
     * @return datasource list
     */
    <T> List<DataSource> listAuthorizedDataSource(@Param("userId") int userId,
                                                  @Param("dataSourceIds") T[] dataSourceIds);

    /**
     * query datasource by name and user id
     *
     * @param userId userId
     * @param name   datasource name
     * @return If the name does not exist or the user does not have permission, it will return null
     */
    DataSource queryDataSourceByNameAndUserId(@Param("userId") int userId, @Param("name") String name);

    /**
     * selectPagingByIds
     * @param dataSourcePage
     * @param dataSourceIds
     * @return
     */
    IPage<DataSource> selectPagingByIds(Page<DataSource> dataSourcePage,
                                        @Param("dataSourceIds") List<Integer> dataSourceIds,
                                        @Param("name") String name);
}
