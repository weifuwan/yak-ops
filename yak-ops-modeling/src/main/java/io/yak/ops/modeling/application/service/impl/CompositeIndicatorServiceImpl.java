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
import io.yak.ops.modeling.api.dto.compositeindicator.CompositeIndicatorDTO;
import io.yak.ops.modeling.api.vo.compositeindicator.CompositeIndicatorVO;
import io.yak.ops.modeling.application.service.CompositeIndicatorService;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;

/** Composite-indicator page business service and relation validation. */
@Service
public class CompositeIndicatorServiceImpl
        extends AbstractVersionedModelingAssetService<ComposeIndicatorPO, CompositeIndicatorDTO, CompositeIndicatorVO>
        implements CompositeIndicatorService {

    private static final List<String> COMPARE_TYPES = Arrays.asList(
            "DAY_ON_LAST_DAY",
            "WEEK_ON_LAST_WEEK",
            "WEEK_ON_WEEK_LAST_YEAR",
            "MONTH_ON_LAST_MONTH",
            "MONTH_ON_MONTH_LAST_YEAR",
            "QUARTER_ON_LAST_QUARTER",
            "YEAR_ON_LAST_YEAR",
            "YEAR_ON_YEAR");

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
        eqIfPresent(wrapper, query.getDwType(), "dw_type");
        eqIfNotBlank(wrapper, query.getMartId(), "mart_id");
        eqIfNotBlank(wrapper, query.getSubjectId(), "subject_id");
        eqIfNotBlank(wrapper, query.getDeriveId(), "derive_id");
        eqIfNotBlank(wrapper, query.getBusTypeId(), "bus_type_id");
        eqIfNotBlank(wrapper, query.getDomainId(), "domain_id");
        eqIfNotBlank(wrapper, query.getBusProcessId(), "bus_process_id");
        eqIfPresent(wrapper, query.getSubmit(), "submit");
        return wrapper;
    }

    @Override
    protected void validate(CompositeIndicatorDTO dto) {
        normalize(dto);
        requireFields(dto, "cnName", "enShortName", "director", "dwType");
        validateUniqueFields(resourceKey(), dto, dto.getId());
        validateComputeMode(dto);
        validateWarehouseOwnership(dto);
    }

    @Override
    protected void beforeCreate(CompositeIndicatorDTO dto) {
        dto.setSubmit(Boolean.FALSE);
    }

    @Override
    protected void beforeUpdate(CompositeIndicatorDTO dto, ComposeIndicatorPO existing) {
        dto.setSubmit(Boolean.FALSE);
    }

    private void normalize(CompositeIndicatorDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("复合指标参数不能为空");
        }
        dto.setEnShortName(text(dto.getEnShortName()));
        dto.setEnName(optionalText(dto.getEnName()));
        dto.setCnName(text(dto.getCnName()));
        dto.setDirector(optionalText(dto.getDirector()));
        dto.setRemark(optionalText(dto.getRemark()));
        dto.setComputeMode(text(dto.getComputeMode()));
        dto.setComputeExpr(optionalText(dto.getComputeExpr()));
        dto.setDeriveId(optionalText(dto.getDeriveId()));
        dto.setCompareType(optionalText(dto.getCompareType()));
        dto.setMartId(optionalText(dto.getMartId()));
        dto.setSubjectId(optionalText(dto.getSubjectId()));
        dto.setBusTypeId(optionalText(dto.getBusTypeId()));
        dto.setDomainId(optionalText(dto.getDomainId()));
        dto.setBusProcessId(optionalText(dto.getBusProcessId()));
    }

    private void validateComputeMode(CompositeIndicatorDTO dto) {
        if ("0".equals(dto.getComputeMode())) {
            requireFields(dto, "computeExpr");
            dto.setDeriveId(null);
            dto.setCompareType(null);
            return;
        }
        if ("1".equals(dto.getComputeMode())) {
            requireFields(dto, "deriveId", "compareType");
            if (!COMPARE_TYPES.contains(dto.getCompareType())) {
                throw new IllegalArgumentException("对比类型不正确");
            }
            validateOptionalReference("derivative-indicator", dto.getDeriveId(), "所选派生指标不存在或不属于当前项目");
            dto.setComputeExpr(null);
            return;
        }
        throw new IllegalArgumentException("计算模式必须是 0（计算表达式）或 1（同环比）");
    }

    private void validateWarehouseOwnership(CompositeIndicatorDTO dto) {
        DwTypeEnum dwType = dto.getDwType();
        if (dwType == DwTypeEnum.PUBLIC_LAYER) {
            dto.setMartId(null);
            dto.setSubjectId(null);
            validatePublicLayerOwnership(dto);
            return;
        }
        if (dwType == DwTypeEnum.APPLICATION_LAYER) {
            dto.setBusTypeId(null);
            dto.setDomainId(null);
            dto.setBusProcessId(null);
            validateApplicationLayerOwnership(dto);
            return;
        }
        throw new IllegalArgumentException("复合指标仅支持公共层或应用层");
    }

    private void validatePublicLayerOwnership(CompositeIndicatorDTO dto) {
        if (notBlank(dto.getBusTypeId()) && businessTypeDao.queryById(dto.getBusTypeId()) == null) {
            throw new IllegalArgumentException("所选业务分类不存在或不属于当前项目");
        }
        if (!notBlank(dto.getDomainId()) || !notBlank(dto.getBusProcessId())) {
            throw new IllegalArgumentException("公共层必须选择数据域及业务过程");
        }

        DataDomainPO domain = dataDomainDao.queryById(dto.getDomainId());
        if (domain == null) {
            throw new IllegalArgumentException("所选数据域不存在或不属于当前项目");
        }
        BusinessProcessPO process = businessProcessDao.queryById(dto.getBusProcessId());
        if (process == null) {
            throw new IllegalArgumentException("所选业务过程不存在或不属于当前项目");
        }
        if (!dto.getDomainId().equals(process.getDataDomainId())) {
            throw new IllegalArgumentException("所选业务过程不属于当前数据域");
        }

        if (notBlank(dto.getBusTypeId())) {
            List<BusinessTypeRelatedDataDomainPO> relations = businessTypeRelatedDataDomainDao.queryList(
                    new QueryWrapper<BusinessTypeRelatedDataDomainPO>()
                            .eq("bus_type_id", dto.getBusTypeId()));
            boolean hasRelatedDomains = false;
            boolean selectedDomainRelated = false;
            if (relations != null) {
                for (BusinessTypeRelatedDataDomainPO relation : relations) {
                    if (relation == null || !notBlank(relation.getDataId())) {
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

    private void validateApplicationLayerOwnership(CompositeIndicatorDTO dto) {
        if (!notBlank(dto.getMartId())) {
            throw new IllegalArgumentException("应用层必须选择数据集市");
        }
        DataMartPO mart = dataMartDao.queryById(dto.getMartId());
        if (mart == null) {
            throw new IllegalArgumentException("所选数据集市不存在或不属于当前项目");
        }
        if (!notBlank(dto.getSubjectId())) {
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

    private String optionalText(String value) {
        String normalized = text(value);
        return normalized.isEmpty() ? null : normalized;
    }
}
