package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.dao.entity.ComposeIndicatorPO;
import io.yak.ops.dao.entity.DerivativeIndicatorPO;
import io.yak.ops.modeling.api.dto.derivedindicator.DerivedIndicatorDTO;
import io.yak.ops.modeling.api.vo.derivedindicator.DerivedIndicatorVO;
import io.yak.ops.modeling.application.service.DerivedIndicatorService;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Derived-indicator page business service. */
@Service
public class DerivedIndicatorServiceImpl
        extends AbstractVersionedModelingAssetService<DerivativeIndicatorPO, DerivedIndicatorDTO, DerivedIndicatorVO>
        implements DerivedIndicatorService {

    @Override
    protected String resourceKey() {
        return "derivative-indicator";
    }

    @Override
    protected String versionType() {
        return "DERIVATIVE_INDICATOR";
    }

    @Override
    protected Class<DerivativeIndicatorPO> entityClass() {
        return DerivativeIndicatorPO.class;
    }

    @Override
    protected Class<DerivedIndicatorDTO> dtoClass() {
        return DerivedIndicatorDTO.class;
    }

    @Override
    protected Class<DerivedIndicatorVO> viewClass() {
        return DerivedIndicatorVO.class;
    }

    @Override
    protected QueryWrapper<DerivativeIndicatorPO> buildQueryWrapper(DerivedIndicatorDTO query) {
        QueryWrapper<DerivativeIndicatorPO> wrapper = new QueryWrapper<DerivativeIndicatorPO>();
        addSearch(wrapper, query, "cn_name", "en_short_name", "en_name");
        eqIfNotBlank(wrapper, query.getAtomicId(), "atomic_id");
        eqIfNotBlank(wrapper, query.getTimePeriodId(), "time_period_id");
        eqIfNotBlank(wrapper, query.getBusTypeId(), "bus_type_id");
        eqIfNotBlank(wrapper, query.getDomainId(), "domain_id");
        eqIfNotBlank(wrapper, query.getBusProcessId(), "bus_process_id");
        eqIfPresent(wrapper, query.getSubmit(), "submit");
        return wrapper;
    }

    @Override
    protected void validate(DerivedIndicatorDTO dto) {
        requireFields(dto, "cnName", "enShortName", "atomicId", "timePeriodId");
        validateUniqueFields(resourceKey(), dto, dto.getId());
        validateOptionalReference("atom-indicator", dto.getAtomicId(), "原子指标不存在");
        validateOptionalReference("time-period", dto.getTimePeriodId(), "时间周期不存在");
        normalizeModifierIds(dto);
    }

    @Override
    protected void beforeCreate(DerivedIndicatorDTO dto) {
        dto.setSubmit(Boolean.FALSE);
    }

    @Override
    protected void beforeUpdate(DerivedIndicatorDTO dto, DerivativeIndicatorPO existing) {
        dto.setSubmit(Boolean.FALSE);
    }

    @Override
    protected void beforeDelete(DerivativeIndicatorPO existing) {
        if (modelingAssetDao.queryCount("compose-indicator",
                new QueryWrapper<ComposeIndicatorPO>().eq("derive_id", existing.getId())) > 0) {
            throw new IllegalArgumentException("该派生指标已被复合指标引用，不能删除");
        }
    }

    private void normalizeModifierIds(DerivedIndicatorDTO dto) {
        String raw = text(dto.getModifierId());
        if (raw.isEmpty()) {
            return;
        }
        Set<String> ids = new LinkedHashSet<String>();
        for (String part : raw.split("[,，]")) {
            String id = part.trim();
            if (id.isEmpty()) {
                continue;
            }
            requireExists("modifier", id, "所选修饰词不存在或不属于当前项目：" + id);
            ids.add(id);
        }
        dto.setModifierId(String.join(",", ids));
    }
}
