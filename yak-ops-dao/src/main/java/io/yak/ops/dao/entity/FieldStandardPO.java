package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Modeling asset persistence entity. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_yak_ops_field_standard")
public class FieldStandardPO extends ModelingBasePO {
    private String stdCode;
            private String enShortName;
            private String enName;
            private String cnName;
            private String fieldDataType;
            private Integer fieldLength;
            private Integer fieldPrecision;
            private Boolean fieldCanNull;
            private String fieldDefaultValue;
            private String businessDefinition;
            private String parentStd;
            private String parentId;
            private String reference;
}
