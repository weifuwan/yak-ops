package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.dao.entity.AtomIndicatorPO;
import io.yak.ops.dao.entity.BusinessProcessPO;
import io.yak.ops.dao.entity.BusinessTypeRelatedDataDomainPO;
import io.yak.ops.dao.entity.DerivativeIndicatorPO;
import io.yak.ops.dao.entity.enums.DwTypeEnum;
import io.yak.ops.dao.repository.modeling.BusinessProcessDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeRelatedDataDomainDao;
import io.yak.ops.dao.repository.modeling.DataDomainDao;
import io.yak.ops.modeling.api.dto.atomindicator.AtomIndicatorDTO;
import io.yak.ops.modeling.api.vo.atomindicator.AtomIndicatorVO;
import io.yak.ops.modeling.application.service.AtomIndicatorService;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;

/** Atomic-indicator page business service. */
@Service
public class AtomIndicatorServiceImpl
        extends AbstractVersionedModelingAssetService<AtomIndicatorPO, AtomIndicatorDTO, AtomIndicatorVO>
        implements AtomIndicatorService {

    private static final Set<String> CALCULATE_FUNCTIONS = new HashSet<String>(Arrays.asList(
            "COUNT", "SUM", "AVG", "MAX", "MIN", "RATE", "RANK", "OTHER"));

    @Resource
    private BusinessTypeDao businessTypeDao;

    @Resource
    private BusinessTypeRelatedDataDomainDao businessTypeRelatedDataDomainDao;

    @Resource
    private DataDomainDao dataDomainDao;

    @Resource
    private BusinessProcessDao businessProcessDao;

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
        addSearch(wrapper, query, "cn_name", "en_short_name", "en_name");
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
        normalize(dto);
        requireFields(dto, "cnName", "enShortName", "businessCaliber", "director", "domainId", "busProcessId");
        validateLengths(dto);

        if (dto.getCalculateFunction() != null && !CALCULATE_FUNCTIONS.contains(dto.getCalculateFunction())) {
            throw new IllegalArgumentException("计算函数不合法");
        }
        if (dto.getDecimalNumber() != null && dto.getDecimalNumber().intValue() < 0) {
            throw new IllegalArgumentException("小数位数不能小于0");
        }

        validateBusinessReferences(dto);
        validateUniqueFields(resourceKey(), dto, dto.getId());
        validateOptionalReference("unit-standard", dto.getUnitId(), "度量单位不存在或不属于当前项目");
    }

    @Override
    protected void beforeCreate(AtomIndicatorDTO dto) {
        if (dto.getDwType() == null) {
            dto.setDwType(DwTypeEnum.PUBLIC_LAYER);
        }
        if (dto.getDecimalNumber() == null) {
            dto.setDecimalNumber(Integer.valueOf(0));
        }
        if (dto.getRemoveDuplicates() == null) {
            dto.setRemoveDuplicates(Boolean.TRUE);
        }
        dto.setSubmit(Boolean.FALSE);
    }

    @Override
    protected void beforeUpdate(AtomIndicatorDTO dto, AtomIndicatorPO existing) {
        if (dto.getDwType() == null) {
            dto.setDwType(existing.getDwType() == null ? DwTypeEnum.PUBLIC_LAYER : existing.getDwType());
        }
        if (dto.getDecimalNumber() == null) {
            dto.setDecimalNumber(existing.getDecimalNumber() == null ? Integer.valueOf(0) : existing.getDecimalNumber());
        }
        if (dto.getRemoveDuplicates() == null) {
            dto.setRemoveDuplicates(existing.getRemoveDuplicates() == null ? Boolean.TRUE : existing.getRemoveDuplicates());
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

    private void normalize(AtomIndicatorDTO dto) {
        dto.setDwType(DwTypeEnum.PUBLIC_LAYER);
        dto.setEnShortName(text(dto.getEnShortName()));
        dto.setEnName(optionalText(dto.getEnName()));
        dto.setCnName(text(dto.getCnName()));
        dto.setDirector(text(dto.getDirector()));
        dto.setRemark(optionalText(dto.getRemark()));
        dto.setBusinessCaliber(text(dto.getBusinessCaliber()));
        dto.setBusTypeId(optionalText(dto.getBusTypeId()));
        dto.setDomainId(optionalText(dto.getDomainId()));
        dto.setBusProcessId(optionalText(dto.getBusProcessId()));
        dto.setUnitId(optionalText(dto.getUnitId()));
        String calculateFunction = optionalText(dto.getCalculateFunction());
        dto.setCalculateFunction(calculateFunction == null ? null : calculateFunction.toUpperCase(Locale.ROOT));
    }

    private void validateLengths(AtomIndicatorDTO dto) {
        validateLength(dto.getEnShortName(), 500, "英文缩写");
        validateLength(dto.getEnName(), 128, "英文名称");
        validateLength(dto.getCnName(), 2048, "中文名称");
        validateLength(dto.getDirector(), 32, "负责人");
        validateLength(dto.getBusinessCaliber(), 500, "业务口径");
        validateLength(dto.getRemark(), 2048, "描述");
    }

    private void validateLength(String value, int maxLength, String label) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(label + "不能超过" + maxLength + "个字符");
        }
    }

    private void validateBusinessReferences(AtomIndicatorDTO dto) {
        if (notBlank(dto.getBusTypeId()) && businessTypeDao.queryById(dto.getBusTypeId()) == null) {
            throw new IllegalArgumentException("所选业务分类不存在或不属于当前项目");
        }
        if (dataDomainDao.queryById(dto.getDomainId()) == null) {
            throw new IllegalArgumentException("所选数据域不存在或不属于当前项目");
        }

        BusinessProcessPO process = businessProcessDao.queryById(dto.getBusProcessId());
        if (process == null) {
            throw new IllegalArgumentException("所选业务过程不存在或不属于当前项目");
        }
        if (!dto.getDomainId().equals(process.getDataDomainId())) {
            throw new IllegalArgumentException("业务过程不属于所选数据域");
        }

        if (notBlank(dto.getBusTypeId())) {
            LambdaQueryWrapper<BusinessTypeRelatedDataDomainPO> wrapper =
                    new LambdaQueryWrapper<BusinessTypeRelatedDataDomainPO>()
                            .eq(BusinessTypeRelatedDataDomainPO::getBusTypeId, dto.getBusTypeId());
            List<BusinessTypeRelatedDataDomainPO> relatedDomains =
                    businessTypeRelatedDataDomainDao.queryList(wrapper);

            boolean hasRelatedDomains = false;
            boolean selectedDomainRelated = false;
            if (relatedDomains != null) {
                for (BusinessTypeRelatedDataDomainPO relation : relatedDomains) {
                    if (relation == null || !notBlank(relation.getDataId())) {
                        continue;
                    }
                    hasRelatedDomains = true;
                    if (dto.getDomainId().equals(relation.getDataId())) {
                        selectedDomainRelated = true;
                    }
                }
            }
            // Keep strict relation validation when the business type has configured domains.
            // When it has none, allow any existing project domain selected in the indicator form.
            if (hasRelatedDomains && !selectedDomainRelated) {
                throw new IllegalArgumentException("所选数据域未关联到当前业务分类");
            }
        }
    }

    private String optionalText(String value) {
        String normalized = text(value);
        return normalized.isEmpty() ? null : normalized;
    }
}
