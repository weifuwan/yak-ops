package io.yak.ops.dao.entity.datasource;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 映射 yak_ops_sql_execution 表，保存一次 SQL 执行的审计元数据。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Getter
@Setter
@TableName("yak_ops_sql_execution")
public class SqlExecutionAuditEntity {

    /** 审计记录主键，由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** SQL Runtime 生成的执行唯一标识。 */
    private String executionId;

    /** 发起执行的数据源标识。 */
    private String dataSourceId;

    /** 调用方类型，保存业务枚举名称。 */
    private String caller;

    /** 调用方提供的业务引用，例如任务或请求标识。 */
    private String callerReference;

    /** 发起执行的操作人标识，可为空。 */
    private String operatorName;

    /** 事务模式，保存业务枚举名称。 */
    private String transactionMode;

    /** 执行终态，保存业务枚举名称。 */
    private String status;

    /** 本次执行包含的 Statement 数量。 */
    private Integer statementCount;

    /** 执行成功的 Statement 数量。 */
    private Integer succeededStatementCount;

    /** 本次执行累计返回的数据行数。 */
    private Long returnedRows;

    /** 本次执行累计影响的数据行数。 */
    private Long affectedRows;

    /** SQL 执行开始时间。 */
    private LocalDateTime startedAt;

    /** SQL 执行结束时间。 */
    private LocalDateTime finishedAt;

    /** SQL 执行总耗时，单位毫秒。 */
    private Long durationMs;

    /** 执行失败时保留的脱敏错误摘要。 */
    private String errorMessage;

    /** 审计记录写入数据库的时间。 */
    private LocalDateTime createTime;
}
