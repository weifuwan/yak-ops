package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;
/**
 * 智能建模 - 业务过程
 */
@Data
@TableName(value = "t_yak_ops_business_process")
@EqualsAndHashCode(callSuper = true)
public class BusinessProcessPO extends ModelingBasePO {

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

    @TableField(exist = false)
    private String dataDomainCnName;
}
