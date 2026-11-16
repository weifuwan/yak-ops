package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.dao.entity.ComposeIndicatorPO;
import io.yak.ops.modeling.api.dto.compositeindicator.CompositeIndicatorDTO;
import io.yak.ops.modeling.api.vo.compositeindicator.CompositeIndicatorVO;
import io.yak.ops.modeling.application.service.CompositeIndicatorService;
import org.springframework.stereotype.Service;

/** Composite-indicator page business service. */
@Service
public class CompositeIndicatorServiceImpl
        extends AbstractVersionedModelingAssetService<ComposeIndicatorPO, CompositeIndicatorDTO, CompositeIndicatorVO>
        implements CompositeIndicatorService {

    @Override
    protected String resourceKey() {
        return "compose-indicator";
    }

    @Override
    protected String versionType() {
        return "COMPOSE_INDICATOR";
    }

    @Override
    protected Class<ComposeIndicatorPO> entityClass() {
        return ComposeIndicatorPO.class;
    }

    @Override
    protected Class<CompositeIndicatorDTO> dtoClass() {
        return CompositeIndicatorDTO.class;
    }

    @Override
    protected Class<CompositeIndicatorVO> viewClass() {
        return CompositeIndicatorVO.class;
    }

    @Override
    protected QueryWrapper<ComposeIndicatorPO> buildQueryWrapper(CompositeIndicatorDTO query) {
        QueryWrapper<ComposeIndicatorPO> wrapper = new QueryWrapper<ComposeIndicatorPO>();
        addSearch(wrapper, query, "cn_name", "en_short_name", "en_name");
        eqIfNotBlank(wrapper, query.getDeriveId(), "derive_id");
        eqIfNotBlank(wrapper, query.getBusTypeId(), "bus_type_id");
        eqIfNotBlank(wrapper, query.getDomainId(), "domain_id");
        eqIfNotBlank(wrapper, query.getBusProcessId(), "bus_process_id");
        eqIfPresent(wrapper, query.getSubmit(), "submit");
        return wrapper;
    }

    @Override
    protected void validate(CompositeIndicatorDTO dto) {
        requireFields(dto, "cnName", "enShortName");
        validateUniqueFields(resourceKey(), dto, dto.getId());
        String mode = text(dto.getComputeMode());
        if ("0".equals(mode)) {
            requireFields(dto, "computeExpr");
        } else if ("1".equals(mode)) {
            requireFields(dto, "deriveId", "compareType");
            validateOptionalReference("derivative-indicator", dto.getDeriveId(), "派生指标不存在");
        } else {
            throw new IllegalArgumentException("计算模式必须是 0（计算表达式）或 1（同环比）");
        }
    }

    @Override
    protected void beforeCreate(CompositeIndicatorDTO dto) {
        dto.setSubmit(Boolean.FALSE);
    }

    @Override
    protected void beforeUpdate(CompositeIndicatorDTO dto, ComposeIndicatorPO existing) {
        dto.setSubmit(Boolean.FALSE);
    }
}
