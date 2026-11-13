package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Modeling asset persistence entity. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_yak_ops_time_period")
public class TimePeriodPO extends ModelingBasePO {
    private String categoryId;
            private String enShortName;
            private String enName;
            private String cnName;
            private String director;
            private String remark;
            private Boolean submit;
}
