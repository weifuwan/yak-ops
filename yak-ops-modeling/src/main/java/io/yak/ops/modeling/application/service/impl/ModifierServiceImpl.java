package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.yak.ops.dao.entity.BusinessTypeRelatedDataDomainPO;
import io.yak.ops.dao.entity.BusinessTypePO;
import io.yak.ops.dao.entity.CodeDetailPO;
import io.yak.ops.dao.entity.DimensionTableFieldPO;
import io.yak.ops.dao.entity.DimensionTablePO;
import io.yak.ops.dao.entity.DerivativeIndicatorPO;
import io.yak.ops.dao.entity.DwLayerPO;
import io.yak.ops.dao.entity.ModifierPO;
import io.yak.ops.dao.entity.DataDomainPO;
import io.yak.ops.dao.entity.enums.DimensionTableTypeEnum;
import io.yak.ops.dao.entity.enums.DwTypeEnum;
import io.yak.ops.dao.repository.modeling.BusinessTypeDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeRelatedDataDomainDao;
import io.yak.ops.dao.repository.modeling.DataDomainDao;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.dao.repository.modeling.DimensionTableFieldDao;
import io.yak.ops.dao.repository.modeling.DwLayerDao;
import io.yak.ops.modeling.api.dto.dwlayer.DwLayerDTO;
import io.yak.ops.modeling.api.dto.modifier.ModifierDTO;
import io.yak.ops.modeling.api.vo.dwlayer.DwLayerVO;
import io.yak.ops.modeling.api.vo.modifier.ModifierVO;
import io.yak.ops.modeling.application.service.DwLayerService;
import io.yak.ops.modeling.application.service.ModifierService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Resource;
import org.springframework.stereotype.Service;

/** Indicator-modifier page business service and relation validation. */
@Service
public class ModifierServiceImpl
        extends AbstractVersionedModelingAssetService<ModifierPO, ModifierDTO, ModifierVO>
        implements ModifierService {

    @Resource
    private DwLayerDao dwLayerDao;
    @Resource
    private DwLayerService dwLayerService;
    @Resource
    private BusinessTypeDao businessTypeDao;
    @Resource
    private BusinessTypeRelatedDataDomainDao businessTypeRelatedDataDomainDao;
    @Resource
    private DataDomainDao dataDomainDao;
    @Resource
    private DimensionTableDao dimensionTableDao;
    @Resource
    private DimensionTableFieldDao dimensionTableFieldDao;

    @Override
    protected String resourceKey() {
        return "modifier";
    }

    @Override
    protected String versionType() {
        return "MODIFIER";
    }

    @Override
    protected Class<ModifierPO> entityClass() {
        return ModifierPO.class;
    }

    @Override
    protected Class<ModifierDTO> dtoClass() {
        return ModifierDTO.class;
    }

    @Override
    protected Class<ModifierVO> viewClass() {
        return ModifierVO.class;
    }

    @Override
    protected QueryWrapper<ModifierPO> buildQueryWrapper(ModifierDTO query) {
        QueryWrapper<ModifierPO> wrapper = new QueryWrapper<ModifierPO>();
        addKeyword(wrapper, query.getKeyword(), "cn_name", "en_short_name", "en_name");
        eqIfNotBlank(wrapper, query.getModifierType(), "modifier_type");
        eqIfNotBlank(wrapper, query.getDwhId(), "dwh_id");
        eqIfNotBlank(wrapper, query.getBusTypeId(), "bus_type_id");
        eqIfNotBlank(wrapper, query.getDomainId(), "domain_id");
        eqIfPresent(wrapper, query.getSubmit(), "submit");
        applyWarehouseTypeFilter(wrapper, query.getDwType());
        return wrapper;
    }

    @Override
    protected void validate(ModifierDTO dto) {
        requireFields(dto, "cnName", "enShortName", "modifierType", "businessCaliber", "director");
        validateUniqueFields(resourceKey(), dto, dto.getId());
        validateRelations(dto);
    }

    @Override
    protected void beforeCreate(ModifierDTO dto) {
        dto.setSubmit(Boolean.FALSE);
    }

    @Override
    protected void beforeUpdate(ModifierDTO dto, ModifierPO existing) {
        dto.setSubmit(Boolean.FALSE);
    }

    @Override
    protected void beforeSubmit(ModifierDTO dto, ModifierPO current) {
        if ("DIMENSION".equals(text(dto.getModifierType()).toUpperCase(Locale.ROOT))) {
            requireFields(dto, "dimensionTbId", "dimensionFdId", "codeId", "codeDetailId");
        }
    }

    @Override
    protected void beforeDelete(ModifierPO existing) {
        QueryWrapper<DerivativeIndicatorPO> wrapper = new QueryWrapper<DerivativeIndicatorPO>()
                .and(condition -> condition
                        .eq("modifier_id", existing.getId())
                        .or().likeRight("modifier_id", existing.getId() + ",")
                        .or().like("modifier_id", "," + existing.getId() + ",")
                        .or().likeLeft("modifier_id", "," + existing.getId()));
        if (modelingAssetDao.queryCount("derivative-indicator", wrapper) > 0) {
            throw new IllegalArgumentException("该修饰词已被派生指标引用，不能删除");
        }
    }

    private void validateRelations(ModifierDTO dto) {
        String modifierType = text(dto.getModifierType()).toUpperCase(Locale.ROOT);
        if (!"SIMPLE".equals(modifierType) && !"DIMENSION".equals(modifierType)) {
            throw new IllegalArgumentException("修饰词类型必须是 SIMPLE 或 DIMENSION");
        }
        dto.setModifierType(modifierType);

        String dwhId = text(dto.getDwhId());
        if (!dwhId.isEmpty()) {
            DwLayerPO layer = dwLayerDao.queryById(dwhId);
            if (layer == null) {
                throw new IllegalArgumentException("所选数仓分层不存在或不属于当前项目");
            }
            if (layer.getDwType() == null || layer.getDwType() == DwTypeEnum.SOURCE_LAYER) {
                throw new IllegalArgumentException("修饰词不允许关联 ODS 贴源层");
            }
        }

        String busTypeId = text(dto.getBusTypeId());
        if (!busTypeId.isEmpty() && businessTypeDao.queryById(busTypeId) == null) {
            throw new IllegalArgumentException("所选业务分类不存在或不属于当前项目");
        }
        String domainId = text(dto.getDomainId());
        if (!domainId.isEmpty() && dataDomainDao.queryById(domainId) == null) {
            throw new IllegalArgumentException("所选数据域不存在或不属于当前项目");
        }
        if (!busTypeId.isEmpty() && !domainId.isEmpty()) {
            long relationCount = businessTypeRelatedDataDomainDao.queryCount(
                    new QueryWrapper<BusinessTypeRelatedDataDomainPO>().eq("bus_type_id", busTypeId));
            if (relationCount > 0 && businessTypeRelatedDataDomainDao.queryCount(
                    new QueryWrapper<BusinessTypeRelatedDataDomainPO>()
                            .eq("bus_type_id", busTypeId).eq("data_id", domainId)) == 0) {
                throw new IllegalArgumentException("所选数据域未关联到当前业务分类");
            }
        }

        String dimensionTableId = text(dto.getDimensionTbId());
        String dimensionFieldId = text(dto.getDimensionFdId());
        String codeId = text(dto.getCodeId());
        String codeDetailId = text(dto.getCodeDetailId());

        if ("SIMPLE".equals(modifierType)) {
            if (!dimensionTableId.isEmpty() || !dimensionFieldId.isEmpty()
                    || !codeId.isEmpty() || !codeDetailId.isEmpty()) {
                throw new IllegalArgumentException("普通业务修饰词不能配置维度枚举关联");
            }
            return;
        }

        if (!dimensionTableId.isEmpty()) {
            DimensionTablePO table = dimensionTableDao.queryById(dimensionTableId);
            if (table == null) {
                throw new IllegalArgumentException("所选维度表不存在或不属于当前项目");
            }
            if (table.getTableType() != DimensionTableTypeEnum.DIM) {
                throw new IllegalArgumentException("维度枚举修饰词只能关联维度类型的数据表");
            }
        }

        if (!dimensionFieldId.isEmpty()) {
            if (dimensionTableId.isEmpty()) {
                throw new IllegalArgumentException("选择维度字段前必须先选择维度表");
            }
            DimensionTableFieldPO field = dimensionTableFieldDao.queryOne(
                    new QueryWrapper<DimensionTableFieldPO>()
                            .eq("id", dimensionFieldId).eq("table_id", dimensionTableId));
            if (field == null) {
                throw new IllegalArgumentException("所选维度字段不存在或不属于当前维度表");
            }
            String fieldCodeId = text(field.getCodeStdId());
            if (!fieldCodeId.isEmpty()) {
                if (codeId.isEmpty()) {
                    codeId = fieldCodeId;
                    dto.setCodeId(codeId);
                } else if (!fieldCodeId.equals(codeId)) {
                    throw new IllegalArgumentException("所选维度字段已关联其他标准代码，不能选择不一致的标准代码");
                }
            }
        }

        if (!codeId.isEmpty() && modelingAssetDao.queryById("code-standard", codeId) == null) {
            throw new IllegalArgumentException("所选标准代码不存在或不属于当前项目");
        }
        if (!codeDetailId.isEmpty()) {
            if (codeId.isEmpty()) {
                throw new IllegalArgumentException("选择代码明细前必须先选择标准代码");
            }
            CodeDetailPO detail = modelingAssetDao.queryById("code-detail", codeDetailId);
            if (detail == null || !codeId.equals(detail.getCodeId())) {
                throw new IllegalArgumentException("所选代码明细不属于当前标准代码");
            }
        }
    }

    private void applyWarehouseTypeFilter(QueryWrapper<ModifierPO> wrapper, DwTypeEnum dwType) {
        if (dwType == null) {
            return;
        }
        if (dwType == DwTypeEnum.SOURCE_LAYER) {
            throw new IllegalArgumentException("修饰词不允许关联或筛选 ODS 贴源层");
        }
        DwLayerDTO filter = new DwLayerDTO();
        filter.setDwType(dwType);
        List<DwLayerVO> layers = dwLayerService.all(filter);
        List<String> layerIds = new ArrayList<String>();
        for (DwLayerVO layer : layers) {
            if (notBlank(layer.getId())) {
                layerIds.add(layer.getId());
            }
        }
        if (dwType == DwTypeEnum.NONE_LAYER) {
            wrapper.and(scope -> {
                scope.isNull("dwh_id").or().eq("dwh_id", "");
                if (!layerIds.isEmpty()) {
                    scope.or().in("dwh_id", layerIds);
                }
            });
        } else if (layerIds.isEmpty()) {
            wrapper.apply("1 = 0");
        } else {
            wrapper.in("dwh_id", layerIds);
        }
    }
}
