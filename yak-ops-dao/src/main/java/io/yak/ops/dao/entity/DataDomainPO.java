package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
/**
 * 智能建模 - 数据域
 */
@Data
@TableName(value = "t_yak_ops_data_domain")
@EqualsAndHashCode(callSuper = true)
public class DataDomainPO extends ModelingBasePO {
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
     * 模型评审人，逗号隔开的字符串
     */
    private String reviewer;

    /**
     * 负责人
     */
    private String director;

    /**
     * 备注
     */
    private String remark;

    /**
     * 是否默认, 1: 默认，0：非默认
     */
    private int flag;

}
