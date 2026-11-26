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
@TableName("t_yak_ops_data_sync_table_report")
public class DataSyncTableReport implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String executionId;

    private String tableKey;

    private String sourceTable;

    private String sinkTable;

    private String latestMetricsJson;

    private String finalMetricsJson;

    private Long latestSequence;

    private Long latestCollectedAt;

    private Long finalCollectedAt;

    private Date createTime;

    private Date updateTime;
}
