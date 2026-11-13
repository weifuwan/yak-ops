package io.yak.ops.modeling.api.dto.datadomain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import io.yak.ops.modeling.api.dto.pagination.PaginationBaseDTO;

import java.util.Date;

/**
 * 智能建模 - 数据域
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataDomainDTO extends PaginationBaseDTO {

    /**
     * 主键
     */
    private String id;

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
