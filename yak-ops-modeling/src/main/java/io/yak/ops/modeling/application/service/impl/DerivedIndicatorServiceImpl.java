package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.dao.entity.BusinessProcessPO;
import io.yak.ops.dao.entity.BusinessTypeRelatedDataDomainPO;
import io.yak.ops.dao.entity.ComposeIndicatorPO;
import io.yak.ops.dao.entity.DataDomainPO;
import io.yak.ops.dao.entity.DataMartPO;
import io.yak.ops.dao.entity.DerivativeIndicatorPO;
import io.yak.ops.dao.entity.SubjectDomainPO;
import io.yak.ops.dao.entity.enums.DwTypeEnum;
import io.yak.ops.dao.repository.modeling.BusinessProcessDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeRelatedDataDomainDao;
import io.yak.ops.dao.repository.modeling.DataDomainDao;
import io.yak.ops.dao.repository.modeling.DataMartDao;
import io.yak.ops.dao.repository.modeling.SubjectDomainDao;
import io.yak.ops.modeling.api.dto.derivedindicator.DerivedIndicatorDTO;
import io.yak.ops.modeling.api.vo.derivedindicator.DerivedIndicatorVO;
import io.yak.ops.modeling.application.service.DerivedIndicatorService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/** Derived-indicator page business service. */
@Service
public class DerivedIndicatorServiceImpl
        extends AbstractVersionedModelingAssetService<DerivativeIndicatorPO, DerivedIndicatorDTO, DerivedIndicatorVO>
        implements DerivedIndicatorService {

    @Resource
    private BusinessTypeDao businessTypeDao;

    @Resource
    private BusinessTypeRelatedDataDomainDao businessTypeRelatedDataDomainDao;

    @Resource
    private DataDomainDao dataDomainDao;

    @Resource
    private BusinessProcessDao businessProcessDao;

    @Resource
    private DataMartDao dataMartDao;

    @Resource
    private SubjectDomainDao subjectDomainDao;

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
        if (Boolean.TRUE.equals(query.getDwTypeUnassigned())) {
            wrapper.isNull("dw_type");
        } else {
            eqIfPresent(wrapper, query.getDwType(), "dw_type");
        }
        eqIfNotBlank(wrapper, query.getMartId(), "mart_id");
        eqIfNotBlank(wrapper, query.getSubjectId(), "subject_id");
        eqIfNotBlank(wrapper, query.getBusTypeId(), "bus_type_id");
        eqIfNotBlank(wrapper, query.getDomainId(), "domain_id");
        eqIfNotBlank(wrapper, query.getBusProcessId(), "bus_process_id");
        eqIfPresent(wrapper, query.getSubmit(), "submit");
        return wrapper;
    }

    @Override
    protected void validate(DerivedIndicatorDTO dto) {
        normalize(dto);
        requireFields(dto, "cnName", "enShortName", "atomicId", "timePeriodId");
        validateUniqueFields(resourceKey(), dto, dto.getId());
        validateOptionalReference("atom-indicator", dto.getAtomicId(), "原子指标不存在或不属于当前项目");
        validateOptionalReference("time-period", dto.getTimePeriodId(), "时间周期不存在或不属于当前项目");
        validateWarehouseOwnership(dto);
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

    private void normalize(DerivedIndicatorDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("派生指标参数不能为空");
        }
        dto.setEnShortName(text(dto.getEnShortName()));
        dto.setEnName(optionalText(dto.getEnName()));
        dto.setCnName(text(dto.getCnName()));
        dto.setDirector(optionalText(dto.getDirector()));
        dto.setRemark(optionalText(dto.getRemark()));
        dto.setModifierId(optionalText(dto.getModifierId()));
        dto.setMartId(optionalText(dto.getMartId()));
        dto.setSubjectId(optionalText(dto.getSubjectId()));
        dto.setBusTypeId(optionalText(dto.getBusTypeId()));
        dto.setDomainId(optionalText(dto.getDomainId()));
        dto.setBusProcessId(optionalText(dto.getBusProcessId()));
    }

    private void validateWarehouseOwnership(DerivedIndicatorDTO dto) {
        DwTypeEnum dwType = dto.getDwType();
        if (dwType == null) {
            clearWarehouseOwnership(dto);
            return;
        }
        if (dwType != DwTypeEnum.PUBLIC_LAYER && dwType != DwTypeEnum.APPLICATION_LAYER) {
            throw new IllegalArgumentException("派生指标仅支持公共层或应用层，数仓分层可以不选择");
        }

        if (dwType == DwTypeEnum.PUBLIC_LAYER) {
            dto.setMartId(null);
            dto.setSubjectId(null);
            validatePublicLayerOwnership(dto);
            return;
        }

        dto.setBusTypeId(null);
        dto.setDomainId(null);
        dto.setBusProcessId(null);
        validateApplicationLayerOwnership(dto);
    }

    private void validatePublicLayerOwnership(DerivedIndicatorDTO dto) {
        if (StringUtils.isNotBlank(dto.getBusTypeId())
                && businessTypeDao.queryById(dto.getBusTypeId()) == null) {
            throw new IllegalArgumentException("所选业务分类不存在或不属于当前项目");
        }
        if (StringUtils.isBlank(dto.getDomainId())) {
            throw new IllegalArgumentException("公共层必须选择数据域及业务过程");
        }
        DataDomainPO domain = dataDomainDao.queryById(dto.getDomainId());
        if (domain == null) {
            throw new IllegalArgumentException("所选数据域不存在或不属于当前项目");
        }
        if (StringUtils.isBlank(dto.getBusProcessId())) {
            throw new IllegalArgumentException("公共层必须选择数据域及业务过程");
        }
        BusinessProcessPO process = businessProcessDao.queryById(dto.getBusProcessId());
        if (process == null) {
            throw new IllegalArgumentException("所选业务过程不存在或不属于当前项目");
        }
        if (!dto.getDomainId().equals(process.getDataDomainId())) {
            throw new IllegalArgumentException("所选业务过程不属于当前数据域");
        }

        if (StringUtils.isNotBlank(dto.getBusTypeId())) {
            List<BusinessTypeRelatedDataDomainPO> relations = businessTypeRelatedDataDomainDao.queryList(
                    new QueryWrapper<BusinessTypeRelatedDataDomainPO>()
                            .eq("bus_type_id", dto.getBusTypeId()));
            boolean hasRelatedDomains = false;
            boolean selectedDomainRelated = false;
            if (relations != null) {
                for (BusinessTypeRelatedDataDomainPO relation : relations) {
                    if (relation == null || StringUtils.isBlank(relation.getDataId())) {
                        continue;
                    }
                    hasRelatedDomains = true;
                    if (dto.getDomainId().equals(relation.getDataId())) {
                        selectedDomainRelated = true;
                    }
                }
            }
            if (hasRelatedDomains && !selectedDomainRelated) {
                throw new IllegalArgumentException("所选数据域未关联到当前业务分类");
            }
        }
    }

    private void validateApplicationLayerOwnership(DerivedIndicatorDTO dto) {
        if (StringUtils.isBlank(dto.getMartId())) {
            throw new IllegalArgumentException("应用层必须选择数据集市");
        }
        DataMartPO mart = dataMartDao.queryById(dto.getMartId());
        if (mart == null) {
            throw new IllegalArgumentException("所选数据集市不存在或不属于当前项目");
        }
        if (StringUtils.isBlank(dto.getSubjectId())) {
            throw new IllegalArgumentException("应用层必须选择主题域");
        }
        SubjectDomainPO subject = subjectDomainDao.queryById(dto.getSubjectId());
        if (subject == null) {
            throw new IllegalArgumentException("所选主题域不存在或不属于当前项目");
        }
        if (!dto.getMartId().equals(subject.getDataMartId())) {
            throw new IllegalArgumentException("所选主题域不属于当前数据集市");
        }
    }

    private void clearWarehouseOwnership(DerivedIndicatorDTO dto) {
        dto.setMartId(null);
        dto.setSubjectId(null);
        dto.setBusTypeId(null);
        dto.setDomainId(null);
        dto.setBusProcessId(null);
    }

    private void normalizeModifierIds(DerivedIndicatorDTO dto) {
        String raw = text(dto.getModifierId());
        if (raw.isEmpty()) {
            dto.setModifierId(null);
            return;
        }
        Set<String> ids = new LinkedHashSet<String>();
        for (String part : raw.split("[,，]")) {
            String id = part.trim();
            if (id.isEmpty() || !ids.add(id)) {
                continue;
            }
            requireExists("modifier", id, "所选修饰词不存在或不属于当前项目：" + id);
        }
        dto.setModifierId(ids.isEmpty() ? null : String.join(",", ids));
    }

    private String optionalText(String value) {
        String normalized = text(value);
        return normalized.isEmpty() ? null : normalized;
    }
}
