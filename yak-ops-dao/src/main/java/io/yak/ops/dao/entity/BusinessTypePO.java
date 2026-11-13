package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
/**
 * 智能建模 - 业务分类
 */
@Data
@TableName(value = "t_yak_ops_business_type")
@EqualsAndHashCode(callSuper = true)
public class BusinessTypePO extends ModelingBasePO {

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
