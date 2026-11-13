package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.yak.ops.dao.entity.enums.MartTypeEnum;

/**
 * 智能建模 - 数据集市
 */
@Data
@TableName(value = "t_yak_ops_data_mart")
@EqualsAndHashCode(callSuper = true)
public class DataMartPO extends ModelingBasePO {

    /**
     * 英文缩写
     */
    private String enShortName;

    /**
     * 集市名称
     */
    private String martName;

    /**
     * 集市类型
     */
    private MartTypeEnum martType;

    /**
     * 业务分类关联ID
     */
    private String businessTypeId;

    /**
     * 负责人
     */
    private String director;

    /**
     * 备注
     */
    private String remark;

    /**
     * 父ID
     */
    private String parentId;
}
