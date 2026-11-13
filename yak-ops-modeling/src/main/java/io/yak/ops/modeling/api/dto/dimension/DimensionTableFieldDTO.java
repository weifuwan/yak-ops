package io.yak.ops.modeling.api.dto.dimension;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.yak.ops.dao.entity.enums.DimensionDataTypeEnum;
import io.yak.ops.dao.entity.enums.DimensionFieldTypeEnum;
import lombok.Data;

import java.util.Date;

/** Request model for a dimension table field. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DimensionTableFieldDTO {
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