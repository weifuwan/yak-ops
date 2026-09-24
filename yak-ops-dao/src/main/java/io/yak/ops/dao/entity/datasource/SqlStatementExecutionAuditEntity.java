package io.yak.ops.dao.entity.datasource;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 映射 yak_ops_sql_statement_execution 表，保存单条 Statement 的审计元数据。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Getter
@Setter
@TableName("yak_ops_sql_statement_execution")
public class SqlStatementExecutionAuditEntity {

    /** Statement 审计记录主键，由数据库自增生成。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属 SQL execution 的唯一标识。 */
    private String executionId;

    /** SQL Runtime 生成的 Statement 唯一标识。 */
    private String statementId;

    /** Statement 在本次执行中的顺序，从 0 开始。 */
    private Integer statementIndex;

    /** SQL 语义类型，保存业务枚举名称。 */
    private String statementType;

    /** 字面量脱敏后的 SQL SHA-256 指纹。 */
    private String sqlFingerprint;

    /** 字面量脱敏后的 SQL 预览。 */
    private String sqlPreview;

    /** Statement 执行状态，保存业务枚举名称。 */
    private String status;

    /** 执行结果类型，保存业务枚举名称，可为空。 */
    private String resultType;

    /** Statement 返回的数据行数。 */
    private Long returnedRows;

    /** Statement 影响的数据行数。 */
    private Long affectedRows;

    /** 查询结果是否因最大行数限制被截断。 */
    private Boolean truncated;

    /** Statement 开始时间，未实际执行时可为空。 */
    private LocalDateTime startedAt;

    /** Statement 结束时间。 */
    private LocalDateTime finishedAt;

    /** Statement 执行耗时，单位毫秒。 */
    private Long durationMs;

    /** Statement 失败时保留的脱敏错误摘要。 */
    private String errorMessage;

    /** 审计记录写入数据库的时间。 */
    private LocalDateTime createTime;
}
