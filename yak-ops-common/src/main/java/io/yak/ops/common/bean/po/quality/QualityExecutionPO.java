package io.yak.ops.common.bean.po.quality;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("yak_quality_execution")
public class QualityExecutionPO {
  @TableId(type = IdType.AUTO)
  private Long id;
  private Long projectId;
  private String executionNo;
  private String idempotencyKey;
  private Long monitorId;
  private String monitorName;
  private Long dataSourceId;
  private String dataSourceName;
  private String databaseName;
  private String schemaName;
  private String tableName;
  private String objectName;
  private String triggerType;
  private String executionStatus;
  private String checkResult;
  private Integer totalRules;
  private Integer passedRules;
  private Integer failedRules;
  private Integer errorRules;
  private String operatorName;
  private LocalDateTime queuedAt;
  private LocalDateTime startedAt;
  private LocalDateTime finishedAt;
  private Long durationMs;
  private String errorMessage;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
