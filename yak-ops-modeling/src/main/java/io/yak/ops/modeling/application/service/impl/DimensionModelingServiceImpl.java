package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.common.service.BaseServiceImpl;
import io.yak.ops.dao.entity.BusinessProcessPO;
import io.yak.ops.dao.entity.BusinessTypePO;
import io.yak.ops.dao.entity.BusinessTypeRelatedDataDomainPO;
import io.yak.ops.dao.entity.DataDomainPO;
import io.yak.ops.dao.entity.DataMartPO;
import io.yak.ops.dao.entity.DimensionTableFieldPO;
import io.yak.ops.dao.entity.DimensionDorisPublishPO;
import io.yak.ops.dao.entity.DimensionTablePO;
import io.yak.ops.dao.entity.ModifierPO;
import io.yak.ops.dao.entity.SubjectDomainPO;
import io.yak.ops.dao.entity.VersionManagementPO;
import io.yak.ops.dao.entity.enums.DimensionTableTypeEnum;
import io.yak.ops.dao.entity.enums.DwModelEnum;
import io.yak.ops.dao.entity.enums.DwTypeEnum;
import io.yak.ops.dao.repository.modeling.BusinessProcessDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeDao;
import io.yak.ops.dao.repository.modeling.BusinessTypeRelatedDataDomainDao;
import io.yak.ops.dao.repository.modeling.CodeStandardDao;
import io.yak.ops.dao.repository.modeling.DataDomainDao;
import io.yak.ops.dao.repository.modeling.DataMartDao;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.dao.repository.modeling.DimensionTableFieldDao;
import io.yak.ops.dao.repository.modeling.DimensionDorisPublishDao;
import io.yak.ops.dao.repository.modeling.FieldStandardDao;
import io.yak.ops.dao.repository.modeling.ModifierDao;
import io.yak.ops.dao.repository.modeling.SubjectDomainDao;
import io.yak.ops.dao.repository.modeling.UnitStandardDao;
import io.yak.ops.dao.repository.modeling.VersionManagementDao;
import io.yak.ops.modeling.api.dto.dimension.DimensionTableDTO;
import io.yak.ops.modeling.api.dto.dimension.DimensionTableFieldDTO;
import io.yak.ops.modeling.api.dto.dwlayer.DwLayerDTO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTableFieldVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTableVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTreeNodeVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionVersionHistoryVO;
import io.yak.ops.modeling.api.vo.dwlayer.DwLayerVO;
import io.yak.ops.modeling.api.vo.tree.OptionVO;
import io.yak.ops.modeling.application.service.DimensionModelingService;
import io.yak.ops.modeling.application.service.DimensionTreeBuilder;
import io.yak.ops.modeling.application.service.DwLayerService;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Project-scoped dimension modeling application service migrated from data_modal. */
@Service
public class DimensionModelingServiceImpl extends BaseServiceImpl implements DimensionModelingService {

    private static final String VERSION_TYPE = "DIMENSION_TABLE";

    @Resource private ObjectMapper objectMapper;
    @Resource private DimensionTableDao dimensionTableDao;
    @Resource private BusinessProcessDao businessProcessDao;
    @Resource private BusinessTypeDao businessTypeDao;
    @Resource private BusinessTypeRelatedDataDomainDao businessTypeRelatedDataDomainDao;
    @Resource private DataDomainDao dataDomainDao;
    @Resource private DataMartDao dataMartDao;
    @Resource private FieldStandardDao fieldStandardDao;
    @Resource private CodeStandardDao codeStandardDao;
    @Resource private UnitStandardDao unitStandardDao;
    @Resource private SubjectDomainDao subjectDomainDao;
    @Resource private ModifierDao modifierDao;
    @Resource private DimensionTableFieldDao dimensionTableFieldDao;
    @Resource private DimensionDorisPublishDao dimensionDorisPublishDao;
    @Resource private VersionManagementDao versionManagementDao;
    @Resource private DwLayerService dwLayerService;
    @Resource private DimensionTreeBuilder dimensionTreeBuilder;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DimensionTableVO create(DimensionTableDTO dto) {
        return create(dto, "sys");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DimensionTableVO create(DimensionTableDTO dto, String operator) {
        if (dto == null) throw new IllegalArgumentException("维度建模参数不能为空");
        trimBasicFields(dto);
        normalizeRelations(dto);
        normalizeTypeFields(dto, null);
        validateBasicInfo(dto, null);
        validateBusinessRelations(dto);

        DimensionTablePO po = new DimensionTablePO();
        BeanUtils.copyProperties(dto, po);
        po.setId(null);
        po.setProjectCode(null);
        po.setSubmit(Boolean.FALSE);
        po.initInsert();
        po.setCreateBy(org.apache.commons.lang3.StringUtils.defaultIfBlank(operator, "sys"));
        po.setUpdateBy(org.apache.commons.lang3.StringUtils.defaultIfBlank(operator, "sys"));
        if (dimensionTableDao.insert(po) <= 0) {
            throw new IllegalArgumentException("维度建模创建失败");
        }
        return toVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DimensionTableVO updateById(DimensionTableDTO dto) {
        return updateById(dto, "sys");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DimensionTableVO updateById(DimensionTableDTO dto, String operator) {
        if (dto == null || !notBlank(dto.getId())) {
            throw new IllegalArgumentException("主键不能为空");
        }
        trimBasicFields(dto);
        DimensionTablePO existing = dimensionTableDao.queryById(dto.getId());
        if (existing == null) throw new IllegalArgumentException("维度建模记录不存在或不属于当前项目");

        if (existing.getTableType() != null && dto.getTableType() != existing.getTableType()) {
            throw new IllegalArgumentException("模型类型创建后不可直接修改");
        }
        if (existing.getTableType() == null && dto.getTableType() != null
                && !isLegacyUnlayeredRecord(existing)) {
            throw new IllegalArgumentException("历史模型类型缺失记录只能在未分层状态下补充类型");
        }

        if (existing.getTableType() == DimensionTableTypeEnum.DIM
                && dto.getTableType() != DimensionTableTypeEnum.DIM
                && modifierDao.queryCount(new QueryWrapper<ModifierPO>().eq("dimension_tb_id", dto.getId())) > 0) {
            throw new IllegalArgumentException("该维度表已被修饰词引用，不能修改为非维度类型");
        }
        normalizeRelations(dto);
        normalizeTypeFields(dto, existing);
        validateBasicInfo(dto, existing);
        validateBusinessRelations(dto);

        DimensionTablePO po = new DimensionTablePO();
        BeanUtils.copyProperties(dto, po);
        po.setId(dto.getId());
        po.setProjectCode(null);
        po.setCreateTime(existing.getCreateTime());
        po.setCreateBy(existing.getCreateBy());
        po.setSubmit(Boolean.FALSE);
        po.initUpdate();
        po.setUpdateBy(org.apache.commons.lang3.StringUtils.defaultIfBlank(operator, "sys"));
        if (!dimensionTableDao.updateById(po)) {
            throw new IllegalArgumentException("维度建模更新失败");
        }

        UpdateWrapper<DimensionTablePO> relations = new UpdateWrapper<>();
        relations.eq("id", dto.getId())
                .set("mart_id", po.getMartId())
                .set("subject_id", po.getSubjectId())
                .set("bus_type_id", po.getBusTypeId())
                .set("domain_id", po.getDomainId())
                .set("bus_process_id", po.getBusProcessId())
                .set("update_time", po.getUpdateTime())
                .set("update_by", po.getUpdateBy());
        if (dimensionTableDao.update(null, relations) <= 0) {
            throw new IllegalArgumentException("维度建模关联信息更新失败");
        }
        return toVO(dimensionTableDao.queryById(dto.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DimensionTableVO submit(String id) {
        return submit(id, "sys");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DimensionTableVO submit(String id, String operator) {
        DimensionTablePO po = requireTable(id);
        DimensionTableDTO dto = new DimensionTableDTO();
        BeanUtils.copyProperties(po, dto);
        normalizeRelations(dto);
        normalizeTypeFields(dto, po);
        validateBasicInfo(dto, po);
        validateBusinessRelations(dto);
        if (po.getTableType() != DimensionTableTypeEnum.DIM) validateFieldsForSubmit(id);

        po.setSubmit(Boolean.TRUE);
        po.initUpdate();
        po.setUpdateBy(org.apache.commons.lang3.StringUtils.defaultIfBlank(operator, "sys"));
        if (!dimensionTableDao.updateById(po)) {
            throw new IllegalArgumentException("维度建模提交失败");
        }
        saveVersion(dimensionTableDao.queryById(id));
        return toVO(dimensionTableDao.queryById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String delete(String id) {
        requireTable(id);
        if (modifierDao.queryCount(new QueryWrapper<ModifierPO>().eq("dimension_tb_id", id)) > 0) {
            throw new IllegalArgumentException("该维度表已被指标修饰词引用，不能删除");
        }
        dimensionTableFieldDao.deleteByWrapper(new QueryWrapper<DimensionTableFieldPO>().eq("table_id", id));
        dimensionDorisPublishDao.deleteByWrapper(
                new QueryWrapper<DimensionDorisPublishPO>().eq("table_id", id));
        versionManagementDao.deleteByWrapper(new QueryWrapper<VersionManagementPO>()
                .eq("source_table_id", id).eq("version_type", VERSION_TYPE));
        boolean deleted = dimensionTableDao.deleteById(id);
        if (!deleted) throw new IllegalArgumentException("删除维度建模失败");
        return id;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteBatch(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Boolean.TRUE;
        for (String id : ids) delete(id);
        return Boolean.TRUE;
    }

    @Override
    public DimensionTableVO selectById(String id) {
        return toVO(requireTable(id));
    }

    @Override
    public IPage<DimensionTableVO> page(DimensionTableDTO dto) {
        DimensionTableDTO query = dto == null ? new DimensionTableDTO() : dto;
        normalizeRelations(query);
        int pageNo = positive(query.getPageNo(), 1);
        int pageSize = Math.min(positive(query.getPageSize(), 10), 100);
        QueryWrapper<DimensionTablePO> wrapper = buildWrapper(query).orderByDesc("update_time");
        IPage<DimensionTablePO> result = dimensionTableDao.queryPage(new Page<DimensionTablePO>(pageNo, pageSize), wrapper);
        List<DimensionTableVO> records = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        Page<DimensionTableVO> page = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        page.setRecords(records);
        return page;
    }

    @Override
    public List<DimensionTreeNodeVO> tree(DimensionTableDTO dto) {
        DimensionTableDTO query = dto == null ? new DimensionTableDTO() : dto;
        normalizeRelations(query);
        return dimensionTreeBuilder.buildTree(query);
    }

    @Override
    public List<DimensionTableVO> all(DimensionTableDTO dto) {
        DimensionTableDTO query = dto == null ? new DimensionTableDTO() : dto;
        normalizeRelations(query);
        return dimensionTableDao.queryList(buildWrapper(query).orderByDesc("update_time"))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<DimensionTableVO> getNoneList(String keyword) {
        DimensionTableDTO dto = new DimensionTableDTO();
        dto.setDwhType(DwTypeEnum.NONE_LAYER);
        dto.setKeyword(keyword);
        return all(dto);
    }

    @Override
    public List<OptionVO> getSelectOption() {
        return all(new DimensionTableDTO()).stream().map(item -> {
            OptionVO option = new OptionVO();
            option.setLabel(item.getCnName());
            option.setValue(item.getId());
            return option;
        }).collect(Collectors.toList());
    }

    @Override
    public List<OptionVO> getDimOption() {
        QueryWrapper<DimensionTablePO> wrapper = new QueryWrapper<>();
        wrapper.eq("table_type", DimensionTableTypeEnum.DIM.getCode()).orderByAsc("cn_name");
        return dimensionTableDao.queryList(wrapper).stream().map(item -> {
            OptionVO option = new OptionVO();
            option.setLabel(item.getCnName());
            option.setValue(item.getId());
            return option;
        }).collect(Collectors.toList());
    }

    @Override
    public List<DimensionVersionHistoryVO> getVersionHistory(String id) {
        requireTable(id);
        return versionManagementDao.queryList(new QueryWrapper<VersionManagementPO>()
                        .eq("source_table_id", id).eq("version_type", VERSION_TYPE)
                        .orderByDesc("version_number"))
                .stream()
                .map(item -> DimensionVersionHistoryVO.builder()
                        .key(item.getId())
                        .versionNumber(item.getVersionNumber())
                        .updateBy(item.getUpdateBy())
                        .updateTime(item.getUpdateTime())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DimensionTableVO rollbackToVersion(String id, Integer targetVersion) {
        if (!notBlank(id)) throw new IllegalArgumentException("主键不能为空");
        if (targetVersion == null || targetVersion <= 0) throw new IllegalArgumentException("目标版本号必须大于0");
        requireTable(id);
        VersionManagementPO version = versionManagementDao.queryOne(new QueryWrapper<VersionManagementPO>()
                .eq("source_table_id", id)
                .eq("version_type", VERSION_TYPE)
                .eq("version_number", targetVersion));
        if (version == null) throw new IllegalArgumentException("未找到版本号: " + targetVersion + " 的数据");

        try {
            JsonNode root = objectMapper.readTree(version.getVersionData());
            if (root != null && root.isObject() && root.has("table")) {
                DimensionTableVersionSnapshot snapshot =
                        objectMapper.treeToValue(root, DimensionTableVersionSnapshot.class);
                if (snapshot.table == null) {
                    throw new IllegalArgumentException("版本快照缺少基本信息");
                }
                snapshot.table.setId(id);
                snapshot.table.setProjectCode(null);
                snapshot.table.initUpdate();
                snapshot.table.setUpdateBy("sys");
                restoreSnapshot(snapshot.table, id);
                restoreFields(id, snapshot.fields == null ? Collections.emptyList() : snapshot.fields);
            } else {
                // Backward compatibility: historical versions stored only DimensionTablePO.
                DimensionTablePO snapshot = objectMapper.treeToValue(root, DimensionTablePO.class);
                if (snapshot.getTableType() == DimensionTableTypeEnum.DIM
                        && !notBlank(snapshot.getEnShortName()) && notBlank(snapshot.getTableName())) {
                    snapshot.setEnShortName(snapshot.getTableName());
                    snapshot.setTableName(null);
                }
                snapshot.setId(id);
                snapshot.setProjectCode(null);
                snapshot.initUpdate();
                snapshot.setUpdateBy("sys");
                restoreSnapshot(snapshot, id);
            }
            DimensionTablePO restored = dimensionTableDao.queryById(id);
            saveVersion(restored);
            return toVO(dimensionTableDao.queryById(id));
        } catch (Exception ex) {
            throw new IllegalStateException("维度建模版本回滚失败", ex);
        }
    }

    @Override
    public List<DimensionTableFieldVO> listFields(String tableId) {
        requireTable(tableId);
        return dimensionTableFieldDao.queryList(new QueryWrapper<DimensionTableFieldPO>()
                        .eq("table_id", tableId).orderByAsc("ordinal").orderByAsc("create_time"))
                .stream().map(this::toFieldVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DimensionTableFieldVO createField(String tableId, DimensionTableFieldDTO dto) {
        requireTable(tableId);
        if (dto == null) throw new IllegalArgumentException("字段参数不能为空");
        DimensionTableFieldPO po = toFieldPO(dto, tableId);
        po.setId(null);
        po.setProjectCode(null);
        if (po.getOrdinal() == null) {
            po.setOrdinal(nextFieldOrdinal(tableId));
        }
        po.initInsert();
        dimensionTableFieldDao.insert(po);
        markTableDraft(tableId);
        return toFieldVO(po);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DimensionTableFieldVO updateField(String tableId, String fieldId, DimensionTableFieldDTO dto) {
        requireTable(tableId);
        if (dto == null) throw new IllegalArgumentException("字段参数不能为空");
        DimensionTableFieldPO existing = dimensionTableFieldDao.queryOne(new QueryWrapper<DimensionTableFieldPO>()
                .eq("id", fieldId).eq("table_id", tableId));
        if (existing == null) throw new IllegalArgumentException("维度字段不存在或不属于当前维度表");

        DimensionTableFieldPO po = toFieldPO(dto, tableId);
        if (po.getOrdinal() == null) {
            po.setOrdinal(existing.getOrdinal());
        }
        if (!Objects.equals(existing.getCodeStdId(), po.getCodeStdId())
                && modifierDao.queryCount(new QueryWrapper<ModifierPO>().eq("dimension_fd_id", fieldId)) > 0) {
            throw new IllegalArgumentException("该维度字段已被修饰词引用，不能修改其关联标准代码");
        }
        po.setId(fieldId);
        po.setProjectCode(null);
        po.setCreateTime(existing.getCreateTime());
        po.setCreateBy(existing.getCreateBy());
        po.initUpdate();
        po.setUpdateBy("sys");
        updateFieldColumns(po, tableId, fieldId);
        markTableDraft(tableId);
        return toFieldVO(dimensionTableFieldDao.queryById(fieldId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<DimensionTableFieldVO> replaceFields(String tableId, List<DimensionTableFieldDTO> fields) {
        requireTable(tableId);
        List<DimensionTableFieldPO> existing = dimensionTableFieldDao.queryList(
                new QueryWrapper<DimensionTableFieldPO>().eq("table_id", tableId));
        Map<String, DimensionTableFieldPO> existingById = new HashMap<>();
        for (DimensionTableFieldPO item : existing) existingById.put(item.getId(), item);

        Set<String> retainedIds = new HashSet<>();
        int fieldOrdinal = 0;
        if (fields != null) {
            for (DimensionTableFieldDTO dto : fields) {
                if (dto == null) continue;
                if (notBlank(dto.getId())) {
                    DimensionTableFieldPO old = existingById.get(dto.getId());
                    if (old == null) throw new IllegalArgumentException("字段ID不属于当前维度表: " + dto.getId());
                    DimensionTableFieldPO po = toFieldPO(dto, tableId);
                    po.setOrdinal(dto.getOrdinal() == null ? fieldOrdinal : dto.getOrdinal());
                    fieldOrdinal++;
                    if (!Objects.equals(old.getCodeStdId(), po.getCodeStdId())
                            && modifierDao.queryCount(new QueryWrapper<ModifierPO>().eq("dimension_fd_id", old.getId())) > 0) {
                        throw new IllegalArgumentException("维度字段已被修饰词引用，不能修改其关联标准代码: " + old.getFieldName());
                    }
                    po.setId(old.getId());
                    po.setProjectCode(null);
                    po.setCreateTime(old.getCreateTime());
                    po.setCreateBy(old.getCreateBy());
                    po.initUpdate();
                    po.setUpdateBy("sys");
                    updateFieldColumns(po, tableId, old.getId());
                    retainedIds.add(old.getId());
                } else {
                    DimensionTableFieldPO po = toFieldPO(dto, tableId);
                    po.setOrdinal(dto.getOrdinal() == null ? fieldOrdinal : dto.getOrdinal());
                    fieldOrdinal++;
                    po.setId(null);
                    po.setProjectCode(null);
                    po.initInsert();
                    dimensionTableFieldDao.insert(po);
                    retainedIds.add(po.getId());
                }
            }
        }

        for (DimensionTableFieldPO old : existing) {
            if (!retainedIds.contains(old.getId())) {
                if (modifierDao.queryCount(new QueryWrapper<ModifierPO>().eq("dimension_fd_id", old.getId())) > 0) {
                    throw new IllegalArgumentException("字段已被指标修饰词引用，不能删除: " + old.getFieldName());
                }

                dimensionTableFieldDao.deleteById(old.getId());
            }
        }
        markTableDraft(tableId);
        return listFields(tableId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deleteField(String tableId, String fieldId) {
        requireTable(tableId);
        DimensionTableFieldPO field = dimensionTableFieldDao.queryOne(new QueryWrapper<DimensionTableFieldPO>()
                .eq("id", fieldId).eq("table_id", tableId));
        if (field == null) throw new IllegalArgumentException("维度字段不存在或不属于当前维度表");
        if (modifierDao.queryCount(new QueryWrapper<ModifierPO>().eq("dimension_fd_id", fieldId)) > 0) {
            throw new IllegalArgumentException("该维度字段已被指标修饰词引用，不能删除");
        }

        dimensionTableFieldDao.deleteById(fieldId);
        markTableDraft(tableId);
        return fieldId;
    }

    private QueryWrapper<DimensionTablePO> buildWrapper(DimensionTableDTO dto) {
        QueryWrapper<DimensionTablePO> wrapper = new QueryWrapper<>();
        if (dto.getDwhType() != null) {
            List<String> layerIds = dwLayerService.all(DwLayerDTO.builder().dwType(dto.getDwhType()).build())
                    .stream().map(DwLayerVO::getId).collect(Collectors.toList());
            if (layerIds.isEmpty()) wrapper.eq("dwh_id", "__NO_MATCHING_LAYER__");
            else wrapper.in("dwh_id", layerIds);
        }
        if (notBlank(dto.getDwhId())) wrapper.eq("dwh_id", dto.getDwhId());
        if (dto.getTableType() != null) wrapper.eq("table_type", dto.getTableType());
        if (notBlank(dto.getDomainId())) wrapper.eq("domain_id", dto.getDomainId());
        if (notBlank(dto.getMartId())) wrapper.eq("mart_id", dto.getMartId());
        if (notBlank(dto.getSubjectId())) wrapper.eq("subject_id", dto.getSubjectId());
        if (notBlank(dto.getDimId())) wrapper.eq("dim_id", dto.getDimId());
        if (notBlank(dto.getBusTypeId())) wrapper.eq("bus_type_id", dto.getBusTypeId());
        if (notBlank(dto.getBusProcessId())) wrapper.eq("bus_process_id", dto.getBusProcessId());
        if (notBlank(dto.getUpdateBy())) wrapper.eq("update_by", dto.getUpdateBy());
        if (notBlank(dto.getDirector())) wrapper.eq("director", dto.getDirector());
        if (dto.getSubmit() != null) wrapper.eq("submit", dto.getSubmit());
        if (notBlank(dto.getKeyword())) {
            wrapper.and(q -> q.like("cn_name", dto.getKeyword())
                    .or().like("table_name", dto.getKeyword())
                    .or().like("en_short_name", dto.getKeyword()));
        }
        return wrapper;
    }

    private void trimBasicFields(DimensionTableDTO dto) {
        dto.setId(org.apache.commons.lang3.StringUtils.trimToNull(dto.getId()));
        dto.setDwhId(org.apache.commons.lang3.StringUtils.trimToNull(dto.getDwhId()));
        dto.setEnShortName(org.apache.commons.lang3.StringUtils.trimToNull(dto.getEnShortName()));
        dto.setTableName(org.apache.commons.lang3.StringUtils.trimToNull(dto.getTableName()));
        dto.setCnName(org.apache.commons.lang3.StringUtils.trimToNull(dto.getCnName()));
        dto.setBusTypeId(org.apache.commons.lang3.StringUtils.trimToNull(dto.getBusTypeId()));
        dto.setBusProcessId(org.apache.commons.lang3.StringUtils.trimToNull(dto.getBusProcessId()));
        dto.setDomainId(org.apache.commons.lang3.StringUtils.trimToNull(dto.getDomainId()));
        dto.setMartId(org.apache.commons.lang3.StringUtils.trimToNull(dto.getMartId()));
        dto.setSubjectId(org.apache.commons.lang3.StringUtils.trimToNull(dto.getSubjectId()));
        dto.setTimePeriodId(org.apache.commons.lang3.StringUtils.trimToNull(dto.getTimePeriodId()));
        dto.setDimId(org.apache.commons.lang3.StringUtils.trimToNull(dto.getDimId()));
        dto.setModifier(org.apache.commons.lang3.StringUtils.trimToNull(dto.getModifier()));
        dto.setNameRuleId(org.apache.commons.lang3.StringUtils.trimToNull(dto.getNameRuleId()));
        dto.setDirector(org.apache.commons.lang3.StringUtils.trimToNull(dto.getDirector()));
        dto.setRemark(org.apache.commons.lang3.StringUtils.trimToNull(dto.getRemark()));
        dto.setMartOrSubject(org.apache.commons.lang3.StringUtils.trimToNull(dto.getMartOrSubject()));
    }

    private void normalizeRelations(DimensionTableDTO dto) {
        if (dto.getBusProcessIds() != null && !dto.getBusProcessIds().isEmpty()) {
            if (dto.getBusProcessIds().size() > 2) {
                throw new IllegalArgumentException("数据域/业务过程选择值格式不正确");
            }
            dto.setDomainId(dto.getBusProcessIds().get(0));
            dto.setBusProcessId(dto.getBusProcessIds().size() == 2 ? dto.getBusProcessIds().get(1) : null);
        }
        if (notBlank(dto.getMartOrSubject())) {
            int separator = dto.getMartOrSubject().lastIndexOf('-');
            if (separator <= 0 || separator == dto.getMartOrSubject().length() - 1) {
                throw new IllegalArgumentException("数据集市/主题域选择值格式不正确");
            }
            String id = dto.getMartOrSubject().substring(0, separator);
            String type = dto.getMartOrSubject().substring(separator + 1);
            if ("SUBJECT".equalsIgnoreCase(type)) {
                dto.setSubjectId(id);
                dto.setMartId(null);
            } else if ("MART".equalsIgnoreCase(type)) {
                dto.setMartId(id);
                dto.setSubjectId(null);
            } else {
                throw new IllegalArgumentException("数据集市/主题域类型不正确");
            }
        }
        if (notBlank(dto.getMartId()) && notBlank(dto.getSubjectId())) {
            throw new IllegalArgumentException("数据集市和主题域只能选择其中一项");
        }
    }

    private void validateBusinessRelations(DimensionTableDTO dto) {
        if (notBlank(dto.getDomainId())
                && dataDomainDao.queryById(dto.getDomainId()) == null) {
            throw new IllegalArgumentException("所选数据域不存在或不属于当前项目");
        }

        if (notBlank(dto.getBusProcessId())) {
            BusinessProcessPO process = businessProcessDao.queryById(dto.getBusProcessId());
            if (process == null) {
                throw new IllegalArgumentException("所选业务过程不存在或不属于当前项目");
            }
            if (!notBlank(dto.getDomainId())) {
                dto.setDomainId(process.getDataDomainId());
            } else if (!dto.getDomainId().equals(process.getDataDomainId())) {
                throw new IllegalArgumentException("业务过程不属于所选数据域");
            }
        }

        if (notBlank(dto.getBusTypeId())) {
            BusinessTypePO businessType = businessTypeDao.queryById(dto.getBusTypeId());
            if (businessType == null) {
                throw new IllegalArgumentException("所选业务分类不存在或不属于当前项目");
            }
            if (notBlank(dto.getDomainId())) {
                long linkedDomainCount = businessTypeRelatedDataDomainDao.queryCount(
                        new QueryWrapper<BusinessTypeRelatedDataDomainPO>()
                                .eq("bus_type_id", dto.getBusTypeId()));
                // A category with no links is unrestricted; otherwise the selected
                // domain must be one of the explicitly associated domains.
                if (linkedDomainCount > 0) {
                    long relationCount = businessTypeRelatedDataDomainDao.queryCount(
                            new QueryWrapper<BusinessTypeRelatedDataDomainPO>()
                                    .eq("bus_type_id", dto.getBusTypeId())
                                    .eq("data_id", dto.getDomainId()));
                    if (relationCount == 0) {
                        throw new IllegalArgumentException("所选数据域未关联到当前业务分类");
                    }
                }
            }
        }

        if (notBlank(dto.getMartId())) {
            DataMartPO mart = dataMartDao.queryById(dto.getMartId());
            if (mart == null) {
                throw new IllegalArgumentException("所选数据集市不存在或不属于当前项目");
            }
        }

        if (notBlank(dto.getSubjectId())) {
            SubjectDomainPO subject = subjectDomainDao.queryById(dto.getSubjectId());
            if (subject == null) {
                throw new IllegalArgumentException("所选主题域不存在或不属于当前项目");
            }
            if (notBlank(subject.getDataMartId())
                    && dataMartDao.queryById(subject.getDataMartId()) == null) {
                throw new IllegalArgumentException("主题域所属数据集市不存在或不属于当前项目");
            }
        }
    }

    private void normalizeTypeFields(DimensionTableDTO dto, DimensionTablePO existing) {
        DimensionTableTypeEnum tableType = dto.getTableType();
        if (tableType == DimensionTableTypeEnum.DIM) {
            if (!notBlank(dto.getEnShortName()) && existing != null
                    && !notBlank(existing.getEnShortName()) && notBlank(existing.getTableName())) {
                dto.setEnShortName(existing.getTableName());
            }
            dto.setTableName(null);
            dto.setTimePeriodId(null);
            dto.setDimId(null);
            dto.setModifier(null);
            dto.setNameRuleId(null);
            dto.setStoragePolicy(null);
            dto.setLifeCycle(null);
            dto.setDirector(null);
            dto.setBusProcessId(null);
            dto.setMartId(null);
            dto.setSubjectId(null);
            return;
        }
        if (tableType == null) return;

        dto.setEnShortName(null);
        if (tableType != DimensionTableTypeEnum.DETAIL) dto.setBusProcessId(null);
        if (tableType != DimensionTableTypeEnum.APPLICATION) {
            dto.setMartId(null);
            dto.setSubjectId(null);
        }
        if (tableType != DimensionTableTypeEnum.SUMMARY
                && tableType != DimensionTableTypeEnum.APPLICATION) {
            dto.setTimePeriodId(null);
            dto.setDimId(null);
            dto.setModifier(null);
        }
        if (tableType != DimensionTableTypeEnum.SOURCE
                && tableType != DimensionTableTypeEnum.DIMENSION
                && tableType != DimensionTableTypeEnum.DETAIL) {
            dto.setStoragePolicy(null);
        }
        if (tableType == DimensionTableTypeEnum.APPLICATION) {
            dto.setBusTypeId(null);
            dto.setDomainId(null);
        }
    }

    private void validateBasicInfo(DimensionTableDTO dto, DimensionTablePO existing) {
        boolean legacyUntyped = existing != null
                && existing.getTableType() == null
                && dto.getTableType() == null
                && isLegacyUnlayeredRecord(existing);

        if (!notBlank(dto.getDwhId())) {
            throw new IllegalArgumentException("数仓分层不能为空");
        }
        if (!legacyUntyped && dto.getTableType() == null) {
            throw new IllegalArgumentException("模型类型不能为空");
        }
        if (legacyUntyped) {
            if (!notBlank(dto.getCnName()) || !notBlank(dto.getTableName())) {
                throw new IllegalArgumentException("历史未分层记录必须填写英文名称和中文名称");
            }
            validateWarehouseLayer(dto.getDwhId(), null, existing);
            return;
        }

        validateWarehouseLayer(dto.getDwhId(), dto.getTableType(), existing);
        if (!notBlank(dto.getCnName())) {
            throw new IllegalArgumentException("中文名称不能为空");
        }

        switch (dto.getTableType()) {
            case DIM:
                if (!notBlank(dto.getEnShortName())) {
                    throw new IllegalArgumentException("维度英文缩写不能为空");
                }
                if (!notBlank(dto.getDomainId())) {
                    throw new IllegalArgumentException("维度必须选择数据域");
                }
                break;
            case SOURCE:
            case DIMENSION:
            case SUMMARY:
                requireTableNameAndDirector(dto);
                break;
            case DETAIL:
                requireTableNameAndDirector(dto);
                if (!notBlank(dto.getBusProcessId())) {
                    throw new IllegalArgumentException("明细表必须选择业务过程");
                }
                break;
            case APPLICATION:
                requireTableNameAndDirector(dto);
                if (!notBlank(dto.getMartId()) && !notBlank(dto.getSubjectId())) {
                    throw new IllegalArgumentException("应用表必须选择数据集市或主题域");
                }
                if (notBlank(dto.getMartId()) && notBlank(dto.getSubjectId())) {
                    throw new IllegalArgumentException("数据集市和主题域只能选择其中一项");
                }
                break;
            default:
                throw new IllegalArgumentException("不支持的维度模型类型");
        }
        if (!isUniqueNameUnchanged(dto, existing)) {
            validateUniqueFields(dto, existing == null ? null : existing.getId());
        }
    }

    private boolean isUniqueNameUnchanged(DimensionTableDTO dto, DimensionTablePO existing) {
        if (existing == null || existing.getTableType() != dto.getTableType()) return false;
        if (dto.getTableType() == DimensionTableTypeEnum.DIM) {
            String previous = notBlank(existing.getEnShortName())
                    ? existing.getEnShortName() : existing.getTableName();
            return Objects.equals(previous, dto.getEnShortName());
        }
        return Objects.equals(existing.getTableName(), dto.getTableName());
    }

    private void requireTableNameAndDirector(DimensionTableDTO dto) {
        if (!notBlank(dto.getTableName())) {
            throw new IllegalArgumentException("英文表名不能为空");
        }
        if (!notBlank(dto.getDirector())) {
            throw new IllegalArgumentException("负责人不能为空");
        }
    }

    private void validateWarehouseLayer(
            String dwhId,
            DimensionTableTypeEnum tableType,
            DimensionTablePO existing) {
        if (!notBlank(dwhId)) {
            throw new IllegalArgumentException("数仓分层不能为空");
        }
        DwLayerVO layer = dwLayerService.selectById(dwhId);
        if (layer == null) {
            throw new IllegalArgumentException("数仓分层不存在或不属于当前项目");
        }
        if (layer.getDwType() == DwTypeEnum.NONE_LAYER) {
            if (existing == null || !Objects.equals(existing.getDwhId(), dwhId)
                    || existing.getTableType() != tableType) {
                throw new IllegalArgumentException("未分层仅用于历史数据管理，不能在未分层下新建模型或迁入未分层");
            }
            return;
        }
        if (layer.getDwType() == null
                || layer.getDwModelType() == null
                || !DwModelEnum.canCreate(layer.getDwType(), layer.getDwModelType())) {
            throw new IllegalArgumentException("数仓分层与模型类型配置不合法");
        }
        if (tableType == null || !DwModelEnum.canCreate(layer.getDwModelType(), tableType.getCode())) {
            throw new IllegalArgumentException("当前数仓分层不支持所选维度模型类型");
        }
        // New DIM business dimensions belong to the public layer.
        // Keep previously saved application-layer DIM records editable without migrating their IDs.
        if (tableType == DimensionTableTypeEnum.DIM && layer.getDwType() != DwTypeEnum.PUBLIC_LAYER
                && (existing == null || existing.getTableType() != DimensionTableTypeEnum.DIM
                    || !Objects.equals(existing.getDwhId(), dwhId))) {
            throw new IllegalArgumentException("创建维度仅支持公共层数仓分层");
        }
    }

    private boolean isLegacyUnlayeredRecord(DimensionTablePO existing) {
        if (existing == null || !notBlank(existing.getDwhId())) return false;
        DwLayerVO layer = dwLayerService.selectById(existing.getDwhId());
        return layer != null && layer.getDwType() == DwTypeEnum.NONE_LAYER;
    }

    private void validateUniqueFields(DimensionTableDTO dto, String excludeId) {
        if (dto.getTableType() == DimensionTableTypeEnum.DIM && notBlank(dto.getEnShortName())) {
            QueryWrapper<DimensionTablePO> wrapper = new QueryWrapper<DimensionTablePO>()
                    .eq("table_type", DimensionTableTypeEnum.DIM.getCode())
                    .eq("en_short_name", dto.getEnShortName());
            if (notBlank(excludeId)) wrapper.ne("id", excludeId);
            if (dimensionTableDao.queryCount(wrapper) > 0) {
                throw new IllegalArgumentException("维度英文缩写已存在：" + dto.getEnShortName());
            }
        } else if (dto.getTableType() != null && notBlank(dto.getTableName())) {
            QueryWrapper<DimensionTablePO> wrapper = new QueryWrapper<DimensionTablePO>()
                    .and(q -> q.ne("table_type", DimensionTableTypeEnum.DIM.getCode())
                            .or().isNull("table_type"))
                    .eq("table_name", dto.getTableName());
            if (notBlank(excludeId)) wrapper.ne("id", excludeId);
            if (dimensionTableDao.queryCount(wrapper) > 0) {
                throw new IllegalArgumentException("英文表名已存在：" + dto.getTableName());
            }
        }
    }

    private DimensionTablePO requireTable(String id) {
        if (!notBlank(id)) throw new IllegalArgumentException("维度表ID不能为空");
        DimensionTablePO po = dimensionTableDao.queryById(id);
        if (po == null) throw new IllegalArgumentException("维度建模记录不存在或不属于当前项目");
        return po;
    }

    private DimensionTableVO toVO(DimensionTablePO po) {
        if (po == null) return null;
        DimensionTableVO vo = new DimensionTableVO();
        BeanUtils.copyProperties(po, vo);
        vo.setCurrentVersion(currentVersion(po.getId()));
        if (notBlank(po.getMartId())) vo.setMartOrSubject(po.getMartId() + "-MART");
        if (notBlank(po.getSubjectId())) vo.setMartOrSubject(po.getSubjectId() + "-SUBJECT");
        if (notBlank(po.getDomainId()) && notBlank(po.getBusProcessId())) {
            vo.setBusProcessIds(Arrays.asList(po.getDomainId(), po.getBusProcessId()));
        }
        if (notBlank(po.getDwhId())) {
            DwLayerVO layer = dwLayerService.selectById(po.getDwhId());
            if (layer == null) throw new IllegalStateException("未查到对应数仓分层信息: " + po.getDwhId());
            vo.setDwhName(notBlank(layer.getCnName()) ? layer.getCnName() : layer.getEnShortName());
        }
        return vo;
    }

    private int currentVersion(String id) {
        VersionManagementPO latest = versionManagementDao.queryOne(new QueryWrapper<VersionManagementPO>()
                .eq("source_table_id", id).eq("version_type", VERSION_TYPE)
                .orderByDesc("version_number").last("limit 1"));
        return latest == null || latest.getVersionNumber() == null ? 0 : latest.getVersionNumber();
    }

    private void saveVersion(DimensionTablePO po) {
        try {
            DimensionTableVersionSnapshot snapshot = new DimensionTableVersionSnapshot();
            snapshot.schemaVersion = 4;
            snapshot.table = po;
            snapshot.fields = dimensionTableFieldDao.queryList(new QueryWrapper<DimensionTableFieldPO>()
                    .eq("table_id", po.getId()).orderByAsc("ordinal").orderByAsc("create_time"));
            VersionManagementPO version = new VersionManagementPO();
            version.setSourceTableId(po.getId());
            version.setVersionType(VERSION_TYPE);
            version.setVersionNumber(currentVersion(po.getId()) + 1);
            version.setVersionData(objectMapper.writeValueAsString(snapshot));
            version.setProjectCode(null);
            version.initInsert();
            versionManagementDao.insert(version);
        } catch (Exception ex) {
            throw new IllegalStateException("保存维度建模版本失败", ex);
        }
    }

    private void restoreSnapshot(DimensionTablePO po, String id) {
        UpdateWrapper<DimensionTablePO> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", id)
                .set("dwh_id", po.getDwhId())
                .set("bus_type_id", po.getBusTypeId())
                .set("bus_process_id", po.getBusProcessId())
                .set("domain_id", po.getDomainId())
                .set("mart_id", po.getMartId())
                .set("subject_id", po.getSubjectId())
                .set("time_period_id", po.getTimePeriodId())
                .set("dim_id", po.getDimId())
                .set("modifier", po.getModifier())
                .set("name_rule_id", po.getNameRuleId())
                .set("en_short_name", po.getEnShortName())
                .set("table_name", po.getTableName())
                .set("cn_name", po.getCnName())
                .set("table_type", po.getTableType())
                .set("storage_policy", po.getStoragePolicy())
                .set("life_cycle", po.getLifeCycle())
                .set("director", po.getDirector())
                .set("remark", po.getRemark())
                .set("submit", po.getSubmit())
                .set("update_time", po.getUpdateTime())
                .set("update_by", po.getUpdateBy());
        dimensionTableDao.update(null, wrapper);
    }

    private void restoreFields(String tableId, List<DimensionTableFieldPO> snapshotFields) {
        List<DimensionTableFieldPO> existingFields = dimensionTableFieldDao.queryList(
                new QueryWrapper<DimensionTableFieldPO>().eq("table_id", tableId));
        Map<String, DimensionTableFieldPO> existingById = new HashMap<>();
        for (DimensionTableFieldPO field : existingFields) existingById.put(field.getId(), field);

        Set<String> retainedIds = new HashSet<>();
        for (DimensionTableFieldPO snapshotField : snapshotFields) {
            if (snapshotField == null) continue;
            snapshotField.setTableId(tableId);
            validateFieldStandardReferences(snapshotField);
            snapshotField.setProjectCode(null);
            if (!notBlank(snapshotField.getId())) {
                snapshotField.initInsert();
                dimensionTableFieldDao.insert(snapshotField);
                retainedIds.add(snapshotField.getId());
                continue;
            }

            DimensionTableFieldPO current = existingById.get(snapshotField.getId());
            if (current == null) {
                DimensionTableFieldPO conflicting = dimensionTableFieldDao.queryById(snapshotField.getId());
                if (conflicting != null && !Objects.equals(conflicting.getTableId(), tableId)) {
                    throw new IllegalArgumentException("版本字段ID已被其他维度表占用: " + snapshotField.getId());
                }
                snapshotField.initInsert();
                dimensionTableFieldDao.insert(snapshotField);
            } else {
                if (!Objects.equals(current.getCodeStdId(), snapshotField.getCodeStdId())
                        && modifierDao.queryCount(new QueryWrapper<ModifierPO>()
                                .eq("dimension_fd_id", current.getId())) > 0) {
                    throw new IllegalArgumentException("字段已被指标修饰词引用，无法回滚其标准代码关联: "
                            + current.getFieldName());
                }
                snapshotField.setCreateTime(current.getCreateTime());
                snapshotField.setCreateBy(current.getCreateBy());
                snapshotField.initUpdate();
                snapshotField.setUpdateBy("sys");
                updateFieldColumns(snapshotField, tableId, snapshotField.getId());
            }
            retainedIds.add(snapshotField.getId());
        }

        for (DimensionTableFieldPO current : existingFields) {
            if (retainedIds.contains(current.getId())) continue;
            if (modifierDao.queryCount(new QueryWrapper<ModifierPO>()
                    .eq("dimension_fd_id", current.getId())) > 0) {
                throw new IllegalArgumentException("字段已被指标修饰词引用，无法通过版本回滚删除: "
                        + current.getFieldName());
            }
            dimensionTableFieldDao.deleteById(current.getId());
        }
    }

    private void validateFieldsForSubmit(String tableId) {
        List<DimensionTableFieldPO> fields = dimensionTableFieldDao.queryList(
                new QueryWrapper<DimensionTableFieldPO>().eq("table_id", tableId));
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("提交前至少需要配置一个字段");
        }
        Set<String> fieldNames = new HashSet<>();
        for (DimensionTableFieldPO field : fields) {
            if (!notBlank(field.getFieldName())) {
                throw new IllegalArgumentException("字段英文名称不能为空");
            }
            if (field.getDataType() == null) {
                throw new IllegalArgumentException("字段类型不能为空: " + field.getFieldName());
            }
            String normalized = field.getFieldName().trim().toLowerCase(Locale.ROOT);
            if (!fieldNames.add(normalized)) {
                throw new IllegalArgumentException("字段英文名称重复: " + field.getFieldName());
            }
        }
    }

    private void markTableDraft(String tableId) {
        DimensionTablePO table = requireTable(tableId);
        if (Boolean.TRUE.equals(table.getSubmit())) {
            table.setSubmit(Boolean.FALSE);
            table.initUpdate();
            table.setUpdateBy("sys");
            dimensionTableDao.updateById(table);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class DimensionTableVersionSnapshot {
        public int schemaVersion;
        public DimensionTablePO table;
        public List<DimensionTableFieldPO> fields;

        public DimensionTableVersionSnapshot() {
        }
    }

    private DimensionTableFieldPO toFieldPO(DimensionTableFieldDTO dto, String tableId) {
        DimensionTableFieldPO po = new DimensionTableFieldPO();
        BeanUtils.copyProperties(dto, po);
        po.setTableId(tableId);
        po.setCanNull(dto.getNotNull() == null ? null : !dto.getNotNull());
        if (Boolean.TRUE.equals(po.getPrimaryFlag())) {
            // A logical primary key is always non-null; Doris UNIQUE KEY derives from this flag.
            po.setCanNull(Boolean.FALSE);
        }
        if (po.getFieldType() == null) po.setFieldType(io.yak.ops.dao.entity.enums.DimensionFieldTypeEnum.PROPERTY);
        validateFieldStandardReferences(po);
        po.setProjectCode(null);
        return po;
    }

    private DimensionTableFieldVO toFieldVO(DimensionTableFieldPO po) {
        DimensionTableFieldVO vo = new DimensionTableFieldVO();
        BeanUtils.copyProperties(po, vo);
        vo.setNotNull(po.getCanNull() == null ? null : !po.getCanNull());
        return vo;
    }

    private void validateFieldStandardReferences(DimensionTableFieldPO field) {
        if (notBlank(field.getFieldStdId()) && fieldStandardDao.queryById(field.getFieldStdId()) == null) {
            throw new IllegalArgumentException("关联字段标准不存在或不属于当前项目");
        }
        if (notBlank(field.getCodeStdId()) && codeStandardDao.queryById(field.getCodeStdId()) == null) {
            throw new IllegalArgumentException("关联标准代码不存在或不属于当前项目");
        }
        if (notBlank(field.getUnitId()) && unitStandardDao.queryById(field.getUnitId()) == null) {
            throw new IllegalArgumentException("关联度量单位不存在或不属于当前项目");
        }
    }

    private void updateFieldColumns(DimensionTableFieldPO po, String tableId, String fieldId) {
        UpdateWrapper<DimensionTableFieldPO> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", fieldId).eq("table_id", tableId)
                .set("ordinal", po.getOrdinal())
                .set("field_name", po.getFieldName())
                .set("cn_name", po.getCnName())
                .set("data_type", po.getDataType())
                .set("length", po.getLength())
                .set("field_precision", po.getFieldPrecision())
                .set("field_scale", po.getFieldScale())
                .set("datetime_precision", po.getDatetimePrecision())
                .set("default_value", po.getDefaultValue())
                .set("remark", po.getRemark())
                .set("primary_flag", po.getPrimaryFlag())
                .set("can_null", po.getCanNull())
                .set("source_tb", po.getSourceTb())
                .set("source_field", po.getSourceField())
                .set("field_type", po.getFieldType())
                .set("unit_id", po.getUnitId())
                .set("field_std_id", po.getFieldStdId())
                .set("code_std_id", po.getCodeStdId())
                .set("update_time", po.getUpdateTime())
                .set("update_by", po.getUpdateBy());
        dimensionTableFieldDao.update(null, wrapper);
    }

    private int nextFieldOrdinal(String tableId) {
        List<DimensionTableFieldPO> fields = dimensionTableFieldDao.queryList(
                new QueryWrapper<DimensionTableFieldPO>().eq("table_id", tableId));
        int maxOrdinal = -1;
        for (DimensionTableFieldPO field : safeList(fields)) {
            if (field.getOrdinal() != null) {
                maxOrdinal = Math.max(maxOrdinal, field.getOrdinal());
            }
        }
        return maxOrdinal >= 0 ? maxOrdinal + 1 : safeList(fields).size();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
