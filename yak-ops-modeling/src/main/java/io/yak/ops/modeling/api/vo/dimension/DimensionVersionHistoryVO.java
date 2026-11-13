package io.yak.ops.modeling.api.vo.dimension;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/** Version history row for a dimension table. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DimensionVersionHistoryVO {
    private String key;
    private Integer versionNumber;
    private String updateBy;
    private Date updateTime;
}