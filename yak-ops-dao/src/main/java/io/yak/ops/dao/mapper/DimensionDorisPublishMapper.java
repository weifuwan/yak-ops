package io.yak.ops.dao.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.DimensionDorisPublishPO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface DimensionDorisPublishMapper extends BaseMapper<DimensionDorisPublishPO> {

    /**
     * Query the latest successful publish baseline for one project.
     *
     * <p>The tenant interceptor is intentionally skipped for this statement because
     * its generated wrapper SQL is not required here. Project isolation is enforced
     * explicitly by the mandatory project_code predicate.
     */
    @InterceptorIgnore(tenantLine = "1")
    @Select("SELECT * FROM t_yak_ops_dimension_table_publish "
            + "WHERE table_id = #{tableId} "
            + "AND datasource_id = #{datasourceId} "
            + "AND target_database = #{database} "
            + "AND target_table = #{table} "
            + "AND status = 'SUCCESS' "
            + "AND project_code = #{projectCode} "
            + "ORDER BY create_time DESC "
            + "LIMIT 1")
    DimensionDorisPublishPO selectLatestSuccessful(
            @Param("tableId") String tableId,
            @Param("datasourceId") Integer datasourceId,
            @Param("database") String database,
            @Param("table") String table,
            @Param("projectCode") Long projectCode);
}
