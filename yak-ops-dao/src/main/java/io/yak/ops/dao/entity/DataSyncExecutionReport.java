package io.yak.ops.dao.entity;

import java.io.Serializable;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_yak_ops_data_sync_execution_report")
public class DataSyncExecutionReport implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String executionId;

    private Integer taskInstanceId;

    private String jobId;

    private String deploymentMode;

    private String clusterId;

    private String executionState;

    private String metricsState;

    private Integer reportVersion;

    private Long latestSequence;

    private Long latestCollectedAt;

    private String latestSnapshotJson;

    private Long finalSequence;

    private Long finalCollectedAt;

    private String finalSnapshotJson;

    private Boolean finalized;

    private String lastMetricsError;

    private Date createTime;

    private Date updateTime;
}
