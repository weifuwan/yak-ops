package io.yak.ops.modeling.api.vo.businessprocess;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 智能建模 - 业务过程
 */
@Data
public class BusinessProcessVO {

    /**
     * 主键
     */
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
     * 英文名
     */
    private String enName;

    /**
     * 中文名
     */
    private String cnName;

    /**
     * 数据域ID
     */
    private String dataDomainId;

    /**
     * 数据域中文名称
     */
    private String dataDomainCnName;

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
    private int flag;
}
