package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.yak.ops.dao.entity.enums.DimensionDataTypeEnum;
import io.yak.ops.dao.entity.enums.DimensionFieldTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Field definition belonging to a dimension table. */
@Data
@TableName("t_yak_ops_dimension_table_fields")
@EqualsAndHashCode(callSuper = true)
public class DimensionTableFieldPO extends ModelingBasePO {
    private String tableId;
    private Integer ordinal;
    private String fieldName;
    private String cnName;
    private DimensionDataTypeEnum dataType;
    private Integer length;
    @TableField("field_precision")
    private Integer fieldPrecision;
    private Integer fieldScale;
    private Integer datetimePrecision;
    private String defaultValue;
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