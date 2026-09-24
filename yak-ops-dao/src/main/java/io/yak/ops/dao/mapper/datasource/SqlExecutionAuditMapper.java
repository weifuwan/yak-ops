package io.yak.ops.dao.mapper.datasource;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.dao.entity.datasource.SqlExecutionAuditEntity;
import io.yak.ops.dao.model.datasource.SqlExecutionAuditSummaryRow;
import io.yak.ops.dao.model.datasource.SqlStatementTypeCountRow;
import io.yak.ops.dao.repository.datasource.SqlExecutionAuditRepository.Query;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 提供 SQL execution 审计记录和聚合统计的 MyBatis 查询。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Mapper
public interface SqlExecutionAuditMapper extends BaseMapper<SqlExecutionAuditEntity> {

    /** 按审计筛选条件查询执行记录分页。 */
    IPage<SqlExecutionAuditEntity> selectAuditPage(
            Page<SqlExecutionAuditEntity> page, @Param("query") Query query);

    /** 按审计筛选条件查询执行统计。 */
    SqlExecutionAuditSummaryRow selectAuditSummary(@Param("query") Query query);

    /** 按审计筛选条件查询执行耗时 P95，单位毫秒。 */
    Long selectP95DurationMs(@Param("query") Query query);

    /** 按审计筛选条件查询 Statement 类型分布。 */
    List<SqlStatementTypeCountRow> selectStatementTypeCounts(@Param("query") Query query);
}
