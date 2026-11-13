package io.yak.ops.modeling.infrastructure.persistence.entity.businesstype;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.yak.ops.modeling.infrastructure.persistence.entity.BasePO;

/**
 * 智能建模 - 业务分类
 */
@Data
@TableName(value = "t_cockpit_business_type")
@EqualsAndHashCode(callSuper = true)
public class BusinessTypePO extends BasePO {

    /**
     * 业务名称
     */
    private String businessName;

    /**
     * 英文缩写
     */
    private String enShortName;

    /**
     * 模型评审人，逗号隔开的字符串
     */
    private String reviewer;

    /**
     * 负责人
     */
    private String director;

    /**
     * 父ID
     */
    private String parentId;

    /**
     * 备注
     */
    private String remark;
}
