package io.yak.ops.modeling.api.dto.dimension;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.yak.ops.dao.entity.enums.DorisPartitionUnitEnum;
import java.util.List;
import lombok.Data;

/** Target datasource and optional advanced physical settings for a Doris publish. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DimensionDorisPublishDTO {
    private Integer datasourceId;
    private String targetDatabase;
    private Advanced advanced;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Advanced {
        /** Empty/false means the backend chooses the HASH distribution field automatically. */
        private Boolean customDistribution;
        private List<String> distributionFieldIds;
        /** Null means automatic bucket sizing; a positive value means a fixed bucket count. */
        private Integer bucketCount;
        /** Null means no partitioning. */
        private Partition partition;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Partition {
        private String fieldId;
        private DorisPartitionUnitEnum unit;
    }
}
