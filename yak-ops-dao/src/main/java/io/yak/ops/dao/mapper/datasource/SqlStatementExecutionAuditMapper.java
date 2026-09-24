package io.yak.ops.dao.mapper.datasource;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.datasource.SqlStatementExecutionAuditEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提供 Statement 级 SQL execution 审计记录的 MyBatis 映射。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Mapper
public interface SqlStatementExecutionAuditMapper extends BaseMapper<SqlStatementExecutionAuditEntity> {}
