package io.yak.ops.modeling.api.vo;

import java.util.Date;
import lombok.Data;

/** Common non-sensitive response fields for modeling records. */
@Data
public class ModelingBaseVO {

    private String id;
    private Date createTime;
    private String createBy;
    private Date updateTime;
    private String updateBy;
}
