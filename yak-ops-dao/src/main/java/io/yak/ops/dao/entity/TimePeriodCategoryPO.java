package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Modeling asset persistence entity. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_yak_ops_time_period_category")
public class TimePeriodCategoryPO extends ModelingBasePO {
    private String enName;
            private String cnName;
            private String parentId;
            private Boolean leaf;
}
