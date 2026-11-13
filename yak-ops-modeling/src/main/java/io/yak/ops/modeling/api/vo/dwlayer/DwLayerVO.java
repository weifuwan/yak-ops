package io.yak.ops.modeling.api.vo.dwlayer;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import io.yak.ops.dao.entity.enums.DwModelEnum;
import io.yak.ops.dao.entity.enums.DwTypeEnum;

import java.util.Date;

/**
 * 智能建模 - 数据分层
 */
@Data
public class DwLayerVO {

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
     * 分层类型名称
     */
    private String dwTypeName;

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
    @JsonFormat(pattern = "yyyy年MM月dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy年MM月dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /**
     * 更新人
     */
    private String updateBy;

    public String getDwTypeName() {
        return dwType.getName();
    }

    public void setDwTypeName(String dwTypeName) {
        this.dwTypeName = dwType.getName();
    }
}



