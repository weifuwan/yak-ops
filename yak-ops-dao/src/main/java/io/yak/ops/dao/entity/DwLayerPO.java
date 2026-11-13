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



