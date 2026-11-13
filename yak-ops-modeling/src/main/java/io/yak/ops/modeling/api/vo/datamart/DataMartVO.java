package io.yak.ops.modeling.api.vo.datamart;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import io.yak.ops.dao.entity.enums.MartTypeEnum;

import java.util.Date;

/**
 * 智能建模 - 数据集市
 */
@Data
public class DataMartVO  {

    private String id;
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
     * 集市类型
     */
    private String martTypeName;

    /**
     * 业务分类关联ID
     */
    private String businessTypeId;

    /**
     * 业务分类中文名
     */
    private String businessTypeName;


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
}
