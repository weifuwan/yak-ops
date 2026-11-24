package io.yak.ops.modeling.api.dto.dimension;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.yak.ops.dao.entity.enums.DorisBucketModeEnum;
import io.yak.ops.dao.entity.enums.DorisDataModelEnum;
import io.yak.ops.dao.entity.enums.DorisDistributionTypeEnum;
import io.yak.ops.dao.entity.enums.DorisPartitionStrategyEnum;
import io.yak.ops.dao.entity.enums.DorisPartitionUnitEnum;
import java.util.List;
import java.util.Map;
import lombok.Data;

/** Request model for Doris physical-table configuration. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DimensionDorisConfigDTO {
    private DorisDataModelEnum dataModel;
    private List<String> keyFieldIds;
    private DorisDistributionTypeEnum distributionType;
    private List<String> distributionFieldIds;
    private DorisBucketModeEnum bucketMode;
    private Integer bucketCount;
    private DorisPartitionStrategyEnum partitionStrategy;
    private String partitionFieldId;
    private DorisPartitionUnitEnum partitionUnit;
    private Map<String, String> tableProperties;
}
