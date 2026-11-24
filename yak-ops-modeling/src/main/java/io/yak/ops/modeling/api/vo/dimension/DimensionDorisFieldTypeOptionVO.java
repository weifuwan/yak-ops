package io.yak.ops.modeling.api.vo.dimension;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Field type editor metadata aligned with the Doris SDK type mapper. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DimensionDorisFieldTypeOptionVO {
    private String code;
    private String label;
    /** NONE, LENGTH, DECIMAL or DATETIME_PRECISION. */
    private String parameterType;
    private Integer maxLength;
    private Integer maxPrecision;
    private Integer maxScale;
}
