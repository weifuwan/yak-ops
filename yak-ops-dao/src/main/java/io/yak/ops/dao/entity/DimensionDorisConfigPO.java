package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.dao.entity.enums.DorisBucketModeEnum;
import io.yak.ops.dao.entity.enums.DorisDataModelEnum;
import io.yak.ops.dao.entity.enums.DorisDistributionTypeEnum;
import io.yak.ops.dao.entity.enums.DorisPartitionStrategyEnum;
import io.yak.ops.dao.entity.enums.DorisPartitionUnitEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Doris physical table configuration for one logical dimension-model asset. */
@Data
@TableName("t_yak_ops_dimension_table_doris_config")
@EqualsAndHashCode(callSuper = true)
public class DimensionDorisConfigPO extends ModelingBasePO {
    private String tableId;
    private DorisDataModelEnum dataModel;
    private DorisPartitionStrategyEnum partitionStrategy;
    private String partitionFieldId;
    private DorisPartitionUnitEnum partitionUnit;
    private DorisDistributionTypeEnum distributionType;
    private DorisBucketModeEnum bucketMode;
    private Integer bucketCount;
    @TableField("table_properties")
    private String tableProperties;
}
