package io.yak.ops.dao.mapper.datasource;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.datasource.DataSourceEntity;
import io.yak.ops.dao.model.datasource.DataSourceSummaryRow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供数据源表的 MyBatis 映射和数据库聚合查询。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Mapper
public interface DataSourceMapper extends BaseMapper<DataSourceEntity> {

    /** 查询数据源总量、连通状态和环境分布聚合数据。 */
    DataSourceSummaryRow selectSummary();
}
