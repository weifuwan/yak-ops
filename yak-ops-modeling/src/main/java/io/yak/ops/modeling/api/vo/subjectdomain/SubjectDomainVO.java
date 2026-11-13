package io.yak.ops.modeling.api.vo.subjectdomain;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 智能建模 - 主题域
 */
@Data
public class SubjectDomainVO {

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
     * 所属数据集市名称
     */
    private String dataMartName;

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
