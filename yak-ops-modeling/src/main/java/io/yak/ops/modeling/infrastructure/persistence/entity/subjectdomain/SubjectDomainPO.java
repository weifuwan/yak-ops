package io.yak.ops.modeling.infrastructure.persistence.entity.subjectdomain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.yak.ops.modeling.infrastructure.persistence.entity.BasePO;

/**
 * 智能建模 - 主题域
 */
@Data
@TableName(value = "t_cockpit_subject_domain")
@EqualsAndHashCode(callSuper = true)
public class SubjectDomainPO extends BasePO {

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
     * 备注
     */
    private String remark;

    /**
     * 父ID
     */
    private String parentId;
}
