package io.yak.ops.modeling.infrastructure.persistence.entity.businessprocess;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.yak.ops.modeling.infrastructure.persistence.entity.BasePO;

/**
 * 智能建模 - 业务过程
 */
@Data
@TableName(value = "t_cockpit_business_process")
@EqualsAndHashCode(callSuper = true)
public class BusinessProcessPO extends BasePO {

    /**
     * 英文缩写
     */
    private String enShortName;

    /**
     * 英文名
     */
    private String enName;

    /**
     * 中文名
     */
    private String cnName;

    /**
     * 数据域关联ID
     */
    private String dataDomainId;

    /**
     * 负责人
     */
    private String director;

    /**
     * 备注
     */
    private String remark;


    /**
     * 是否默认
     */
    private int flag;
}
