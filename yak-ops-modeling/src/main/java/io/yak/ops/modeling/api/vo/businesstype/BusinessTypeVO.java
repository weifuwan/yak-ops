package io.yak.ops.modeling.api.vo.businesstype;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 智能建模 - 业务分类
 */
@Data
public class BusinessTypeVO {

    /**
     * 主键ID
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
}
