package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.DatasourceUser;

import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * datasource user realtion mapper interface
 */
public interface DataSourceUserMapper extends BaseMapper<DatasourceUser> {

    /**
     * delete datasource by userId
     * @param userId userId
     * @return delete result
     */
    int deleteByUserId(@Param("userId") int userId);

    /**
     * delete datasource by id
     * @param datasourceId datasourceId
     * @return delete result
     */
    int deleteByDatasourceId(@Param("datasourceId") int datasourceId);

}
