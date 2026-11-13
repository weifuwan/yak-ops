package io.yak.ops.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
/**
 * 业务分类-数据域关联表
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_yak_ops_business_type_related_data_domain")
public class BusinessTypeRelatedDataDomainPO extends ModelingBasePO {

    /**
     * 业务类型ID
     */
    private String busTypeId;
    /**
     * 数据域ID
     */
    private String dataId;
}



