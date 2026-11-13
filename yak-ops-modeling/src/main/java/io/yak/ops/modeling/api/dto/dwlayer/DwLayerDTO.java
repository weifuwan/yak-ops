package io.yak.ops.modeling.api.dto.dwlayer;

import lombok.*;
import io.yak.ops.modeling.api.dto.pagination.PaginationBaseDTO;
import io.yak.ops.modeling.domain.enums.DwModelEnum;
import io.yak.ops.modeling.domain.enums.DwTypeEnum;

import java.util.Date;

/**
 * 智能建模 - 数据分层
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DwLayerDTO extends PaginationBaseDTO {

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
     * 分层类型：DIM-贴源层、DWD-公共层、ADS-应用层、OTHER-其他
     */
    private DwTypeEnum dwType;
    /**
     * 模型类型
     */
    private DwModelEnum dwModelType;
    /**
     * 负责人
     */
    private String director;
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



