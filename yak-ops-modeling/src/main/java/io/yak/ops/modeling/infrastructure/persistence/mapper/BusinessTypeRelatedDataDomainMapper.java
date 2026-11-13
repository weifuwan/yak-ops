package io.yak.ops.modeling.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.modeling.infrastructure.persistence.entity.businesstype.related.BusinessTypeRelatedDataDomainPO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BusinessTypeRelatedDataDomainMapper extends BaseMapper<BusinessTypeRelatedDataDomainPO> {

}
