package io.yak.ops.modeling.application.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.yak.ops.modeling.infrastructure.persistence.entity.businesstype.related.BusinessTypeRelatedDataDomainPO;
import io.yak.ops.modeling.application.service.BusinessTypeRelatedDataDomainService;
import io.yak.ops.modeling.infrastructure.persistence.mapper.BusinessTypeRelatedDataDomainMapper;
import org.springframework.stereotype.Service;

/**
 * 业务分类-数据域
 *
 * @author bruce
 */
@Service
public class BusinessTypeRelatedDataDomainServiceImpl extends ServiceImpl<BusinessTypeRelatedDataDomainMapper, BusinessTypeRelatedDataDomainPO> implements BusinessTypeRelatedDataDomainService {

}