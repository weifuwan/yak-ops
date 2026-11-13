package io.yak.ops.modeling.api.dto.businesstype;

import lombok.Data;
import lombok.EqualsAndHashCode;
import io.yak.ops.modeling.api.dto.pagination.PaginationBaseDTO;

import java.util.Date;

/**
 * 智能建模 - 业务分类
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessTypeDTO extends PaginationBaseDTO {

    /**
     * 主键
     */
    private String id;

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
