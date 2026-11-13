package io.yak.ops.modeling.api.dto.subjectdomain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import io.yak.ops.modeling.api.dto.pagination.PaginationBaseDTO;

import java.util.Date;

/**
 * 智能建模 - 主题域
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SubjectDomainDTO extends PaginationBaseDTO {
    /**
     * 主键
     */
    private String id;
    /**
     * 英文缩写
     */
    private String enShortName;

    /**
     * 主题域名称
     */
    private String domainName;

    /**
     * 负责人
     */
    private String director;

    /**
     * 所属数据集市ID
     */
    private String dataMartId;

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
