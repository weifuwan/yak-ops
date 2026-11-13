package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.yak.ops.dao.entity.enums.DwModelEnum;
import io.yak.ops.dao.entity.enums.DwTypeEnum;

/**
 * 智能建模 - 数据分层
 */
@Data
@TableName(value = "t_yak_ops_dw_layer")
@EqualsAndHashCode(callSuper = true)
public class DwLayerPO extends ModelingBasePO {
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
     * 分层归属：SOURCE_LAYER-贴源层、PUBLIC_LAYER-公共层、APPLICATION_LAYER-应用层、NONE_LAYER-未分层
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



