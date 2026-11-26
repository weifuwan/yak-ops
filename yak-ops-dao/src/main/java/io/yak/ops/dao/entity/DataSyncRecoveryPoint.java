package io.yak.ops.dao.entity;

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
@TableName("t_yak_ops_data_sync_recovery_point")
public class DataSyncRecoveryPoint {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Integer taskInstanceId;

    private Long projectCode;

    private Long taskCode;

    private Integer taskDefinitionVersion;

    private String executionId;

    private String jobId;

    private String runtimeMode;

    private String recoveryType;

    private String restorePath;

    private String state;

    private Date createdAt;

    private Integer usedByInstanceId;
}
