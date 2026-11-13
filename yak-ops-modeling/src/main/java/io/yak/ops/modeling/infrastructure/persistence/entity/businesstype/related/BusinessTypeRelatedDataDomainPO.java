package io.yak.ops.modeling.infrastructure.persistence.entity.businesstype.related;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import io.yak.ops.modeling.infrastructure.persistence.entity.BasePO;


/**
 * 业务分类-数据域关联表
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_cockpit_business_type_related_data_domain")
public class BusinessTypeRelatedDataDomainPO extends BasePO {

    /**
     * 业务类型ID
     */
    private String busTypeId;
    /**
     * 数据域ID
     */
    private String dataId;
}



