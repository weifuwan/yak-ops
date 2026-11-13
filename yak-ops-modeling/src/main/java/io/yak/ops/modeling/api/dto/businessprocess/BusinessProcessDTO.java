package io.yak.ops.modeling.api.dto.businessprocess;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import io.yak.ops.modeling.api.dto.pagination.PaginationBaseDTO;

import java.util.Date;

/**
 * 智能建模 - 业务过程
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder
public class BusinessProcessDTO extends PaginationBaseDTO {

    /**
     * 主键
     */
    private String id;

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
    @Builder.Default
    private Integer flag = 0;
}
