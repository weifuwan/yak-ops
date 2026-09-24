package io.yak.ops.common.bean.dto.observability;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import lombok.Data;

/** Filters for SQL execution history and observability aggregates. */
@Data
public class SqlExecutionAuditQueryDTO {

    @Min(1)
    private Integer pageNo = 1;

    @Min(1)
    @Max(200)
    private Integer pageSize = 20;

    private String executionId;
    private String dataSourceId;
    private String caller;
    private String callerReference;
    private String operatorName;
    private String status;
    private String transactionMode;
    private String statementType;
    private String sqlFingerprint;

    @Min(0)
    private Long minDurationMs;

    private LocalDateTime startedFrom;
    private LocalDateTime startedTo;
}
