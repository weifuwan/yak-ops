package io.yak.ops.modeling.infrastructure.persistence.entity.dimension;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.modeling.domain.enums.DimensionDataTypeEnum;
import io.yak.ops.modeling.domain.enums.DimensionFieldTypeEnum;
import io.yak.ops.modeling.infrastructure.persistence.entity.BasePO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Field definition belonging to a dimension table. */
@Data
@TableName("t_cockpit_dimension_table_fields")
@EqualsAndHashCode(callSuper = true)
public class DimensionTableFieldPO extends BasePO {
    private String tableId;
    private String fieldName;
    private String cnName;
    private DimensionDataTypeEnum dataType;
    private Integer length;
    @TableField("precision")
    private Integer precision;
    private String remark;
    private Boolean primaryFlag;
    /** Physical column is can_null; API exposes the inverse notNull flag. */
    @TableField("can_null")
    private Boolean canNull;
    private String sourceTb;
    private String sourceField;
    private DimensionFieldTypeEnum fieldType;
    private String unitId;
    private String fieldStdId;
    private String codeStdId;
}