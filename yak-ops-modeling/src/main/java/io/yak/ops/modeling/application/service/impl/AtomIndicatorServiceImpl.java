package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.dao.entity.AtomIndicatorPO;
import io.yak.ops.dao.entity.DerivativeIndicatorPO;
import io.yak.ops.dao.entity.enums.DwTypeEnum;
import io.yak.ops.modeling.api.dto.atomindicator.AtomIndicatorDTO;
import io.yak.ops.modeling.api.vo.atomindicator.AtomIndicatorVO;
import io.yak.ops.modeling.application.service.AtomIndicatorService;
import org.springframework.stereotype.Service;

/** Atomic-indicator page business service. */
@Service
public class AtomIndicatorServiceImpl
        extends AbstractVersionedModelingAssetService<AtomIndicatorPO, AtomIndicatorDTO, AtomIndicatorVO>
        implements AtomIndicatorService {

    @Override
    protected String resourceKey() {
        return "atom-indicator";
    }

    @Override
    protected String versionType() {
        return "ATOM_INDICATOR";
    }

    @Override
    protected Class<AtomIndicatorPO> entityClass() {
        return AtomIndicatorPO.class;
    }

    @Override
    protected Class<AtomIndicatorDTO> dtoClass() {
        return AtomIndicatorDTO.class;
    }

    @Override
    protected Class<AtomIndicatorVO> viewClass() {
        return AtomIndicatorVO.class;
    }

    @Override
    protected QueryWrapper<AtomIndicatorPO> buildQueryWrapper(AtomIndicatorDTO query) {
        QueryWrapper<AtomIndicatorPO> wrapper = new QueryWrapper<AtomIndicatorPO>();
        addKeyword(wrapper, query.getKeyword(), "cn_name", "en_short_name", "en_name");
        eqIfPresent(wrapper, query.getDwType(), "dw_type");
        eqIfNotBlank(wrapper, query.getBusTypeId(), "bus_type_id");
        eqIfNotBlank(wrapper, query.getDomainId(), "domain_id");
        eqIfNotBlank(wrapper, query.getBusProcessId(), "bus_process_id");
        eqIfNotBlank(wrapper, query.getUnitId(), "unit_id");
        eqIfPresent(wrapper, query.getSubmit(), "submit");
        return wrapper;
    }

    @Override
    protected void validate(AtomIndicatorDTO dto) {
        requireFields(dto, "cnName", "enShortName", "businessCaliber", "director");
        validateUniqueFields(resourceKey(), dto, dto.getId());
        validateOptionalReference("unit-standard", dto.getUnitId(), "度量单位不存在");
    }

    @Override
    protected void beforeCreate(AtomIndicatorDTO dto) {
        if (dto.getDwType() == null) {
            dto.setDwType(DwTypeEnum.PUBLIC_LAYER);
        }
        dto.setSubmit(Boolean.FALSE);
    }

    @Override
    protected void beforeUpdate(AtomIndicatorDTO dto, AtomIndicatorPO existing) {
        if (dto.getDwType() == null) {
            dto.setDwType(existing.getDwType() == null ? DwTypeEnum.PUBLIC_LAYER : existing.getDwType());
        }
        dto.setSubmit(Boolean.FALSE);
    }

    @Override
    protected void beforeDelete(AtomIndicatorPO existing) {
        if (modelingAssetDao.queryCount("derivative-indicator",
                new QueryWrapper<DerivativeIndicatorPO>().eq("atomic_id", existing.getId())) > 0) {
            throw new IllegalArgumentException("该原子指标已被派生指标引用，不能删除");
        }
    }
}
