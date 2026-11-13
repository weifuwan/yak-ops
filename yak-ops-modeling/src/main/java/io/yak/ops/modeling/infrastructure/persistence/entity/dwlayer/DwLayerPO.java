package io.yak.ops.modeling.infrastructure.persistence.entity.dwlayer;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.yak.ops.modeling.infrastructure.persistence.entity.BasePO;
import io.yak.ops.modeling.domain.enums.DwModelEnum;
import io.yak.ops.modeling.domain.enums.DwTypeEnum;

/**
 * 智能建模 - 数据分层
 */
@Data
@TableName(value = "t_cockpit_dw_layer")
@EqualsAndHashCode(callSuper = true)
public class DwLayerPO extends BasePO {
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
}



