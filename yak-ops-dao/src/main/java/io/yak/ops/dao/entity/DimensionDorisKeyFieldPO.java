package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Ordered UNIQUE KEY field references. */
@Data
@TableName("t_yak_ops_dimension_table_doris_key_fields")
@EqualsAndHashCode(callSuper = true)
public class DimensionDorisKeyFieldPO extends ModelingBasePO {
    private String tableId;
    private String fieldId;
    private Integer ordinal;
}
