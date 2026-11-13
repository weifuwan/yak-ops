package io.yak.ops.modeling.api.dto.datamart;

import lombok.Data;
import lombok.EqualsAndHashCode;
import io.yak.ops.modeling.api.dto.pagination.PaginationBaseDTO;
import io.yak.ops.dao.entity.enums.MartTypeEnum;

import java.util.Date;

/**
 * 智能建模 - 数据集市
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataMartDTO extends PaginationBaseDTO {

    private String id;

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

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 更新人
     */
    private String updateBy;
}
