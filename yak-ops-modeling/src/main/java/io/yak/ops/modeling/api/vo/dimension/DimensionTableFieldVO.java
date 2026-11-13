package io.yak.ops.modeling.api.vo.dimension;

import io.yak.ops.dao.entity.enums.DimensionDataTypeEnum;
import io.yak.ops.dao.entity.enums.DimensionFieldTypeEnum;
import lombok.Data;

import java.util.Date;

/** Response model for a dimension table field. */
@Data
public class DimensionTableFieldVO {
    private String id;
    private String tableId;
    private String fieldName;
    private String cnName;
    private DimensionDataTypeEnum dataType;
    private Integer length;
    private Integer precision;
    private String remark;
    private Boolean primaryFlag;
    private Boolean notNull;
    private String sourceTb;
    private String sourceField;
    private DimensionFieldTypeEnum fieldType;
    private String unitId;
    private String fieldStdId;
    private String codeStdId;
    private Date createTime;
    private String createBy;
    private Date updateTime;
    private String updateBy;
}