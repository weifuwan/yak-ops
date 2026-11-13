package io.yak.ops.modeling.application.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.modeling.api.dto.dimension.DimensionTableDTO;
import io.yak.ops.modeling.api.dto.dimension.DimensionTableFieldDTO;
import io.yak.ops.modeling.api.dto.dwlayer.DwLayerDTO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTableFieldVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTableVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionTreeNodeVO;
import io.yak.ops.modeling.api.vo.dimension.DimensionVersionHistoryVO;
import io.yak.ops.modeling.api.vo.pagination.PaginationResult;
import io.yak.ops.modeling.api.vo.tree.OptionVO;
import io.yak.ops.dao.entity.VersionManagementPO;
import io.yak.ops.dao.entity.DimensionTableFieldPO;
import io.yak.ops.dao.entity.DimensionTablePO;
import io.yak.ops.dao.entity.ModifierPO;
import io.yak.ops.modeling.application.service.DwLayerService;
import io.yak.ops.modeling.api.vo.dwlayer.DwLayerVO;
import io.yak.ops.dao.repository.modeling.DimensionTableDao;
import io.yak.ops.dao.repository.modeling.DimensionTableFieldDao;
import io.yak.ops.dao.repository.modeling.ModifierDao;
import io.yak.ops.dao.repository.modeling.VersionManagementDao;
import io.yak.ops.dao.entity.enums.DimensionTableTypeEnum;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/** Project-scoped dimension modeling application service migrated from data_modal. */
@Service
public class DimensionModelingService {

    private static final String VERSION_TYPE = "DIMENSION_TABLE";

    @Resource private ObjectMapper objectMapper;
    @Resource private DimensionTableDao dimensionTableDao;
    @Resource private ModifierDao modifierDao;
    @Resource private DimensionTableFieldDao dimensionTableFieldDao;
    @Resource private VersionManagementDao versionManagementDao;
    @Resource private DwLayerService dwLayerService;
    @Resource private DimensionTreeBuilder dimensionTreeBuilder;

    @Transactional(rollbackFor = Exception.class)
    public DimensionTableVO create(DimensionTableDTO dto) {
        if (dto == null) throw new IllegalArgumentException("维度建模参数不能为空");
        normalizeRelations(dto);
        validateWarehouseLayer(dto.getDwhId());
        validateUniqueFields(dto.getTableName(), dto.getCnName(), null);

        DimensionTablePO po = new DimensionTablePO();
        BeanUtils.copyProperties(dto, po);
        po.setId(null);
        po.setProjectCode(null);
        po.setSubmit(Boolean.FALSE);
        po.initInsert();
        dimensionTableDao.insert(po);
        return toVO(po);
    }

    @Transactional(rollbackFor = Exception.class)
    public DimensionTableVO updateById(DimensionTableDTO dto) {
        if (dto == null || !notBlank(dto.getId())) {
            throw new IllegalArgumentException("主键不能为空");
        }
        DimensionTablePO existing = dimensionTableDao.queryById(dto.getId());
        if (existing == null) throw new IllegalArgumentException("维度建模记录不存在或不属于当前项目");

        normalizeRelations(dto);
        validateWarehouseLayer(dto.getDwhId());
        validateUniqueFields(dto.getTableName(), dto.getCnName(), dto.getId());

        DimensionTablePO po = new DimensionTablePO();
        BeanUtils.copyProperties(dto, po);
        po.setId(dto.getId());
        po.setProjectCode(null);
        po.setCreateTime(existing.getCreateTime());
        po.setCreateBy(existing.getCreateBy());
        po.setSubmit(Boolean.FALSE);
        po.initUpdate();
        po.setUpdateBy("sys");
        dimensionTableDao.updateById(po);

        // These relation fields may intentionally be cleared when the selected
        // mart/subject or business process changes; update them explicitly.
        UpdateWrapper<DimensionTablePO> relations = new UpdateWrapper<>();
        relations.eq("id", dto.getId())
                .set("mart_id", po.getMartId())
                .set("subject_id", po.getSubjectId())
                .set("bus_type_id", po.getBusTypeId())
                .set("domain_id", po.getDomainId())
                .set("bus_process_id", po.getBusProcessId())
                .set("update_time", po.getUpdateTime())
                .set("update_by", po.getUpdateBy());
        dimensionTableDao.update(null, relations);
        return toVO(dimensionTableDao.queryById(dto.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public DimensionTableVO submit(String id) {
        DimensionTablePO po = requireTable(id);
        po.setSubmit(Boolean.TRUE);
        po.initUpdate();
        po.setUpdateBy("sys");
        dimensionTableDao.updateById(po);
        saveVersion(po);
        return toVO(dimensionTableDao.queryById(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public String delete(String id) {
        requireTable(id);
        if (modifierDao.queryCount(new QueryWrapper<ModifierPO>().eq("dimension_tb_id", id)) > 0) {
            throw new IllegalArgumentException("该维度表已被指标修饰词引用，不能删除");
        }
        dimensionTableFieldDao.deleteByWrapper(new QueryWrapper<DimensionTableFieldPO>().eq("table_id", id));
        versionManagementDao.deleteByWrapper(new QueryWrapper<VersionManagementPO>()
                .eq("source_table_id", id).eq("version_type", VERSION_TYPE));
        int affected = dimensionTableDao.deleteById(id);
        if (affected <= 0) throw new IllegalArgumentException("删除维度建模失败");
        return id;
    }

    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteBatch(List<String> ids) {
        if (ids == null || ids.isEmpty()) return Boolean.TRUE;
        for (String id : ids) delete(id);
        return Boolean.TRUE;
    }

    public DimensionTableVO selectById(String id) {
        return toVO(requireTable(id));
    }

    public PaginationResult<DimensionTableVO> page(DimensionTableDTO dto) {
        DimensionTableDTO query = dto == null ? new DimensionTableDTO() : dto;
        normalizeRelations(query);
        int pageNo = positive(query.getPageNo(), 1);
        int pageSize = Math.min(positive(query.getPageSize(), 10), 100);
        QueryWrapper<DimensionTablePO> wrapper = buildWrapper(query).orderByDesc("update_time");
        IPage<DimensionTablePO> result = dimensionTableDao.queryPage(new Page<DimensionTablePO>(pageNo, pageSize), wrapper);
        List<DimensionTableVO> records = result.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return PaginationResult.buildSuc(records, result);
    }

    public List<DimensionTreeNodeVO> tree(DimensionTableDTO dto) {
        DimensionTableDTO query = dto == null ? new DimensionTableDTO() : dto;
        normalizeRelations(query);
        return dimensionTreeBuilder.buildTree(query);
    }

    public List<DimensionTableVO> all(DimensionTableDTO dto) {
        DimensionTableDTO query = dto == null ? new DimensionTableDTO() : dto;
        normalizeRelations(query);
        return dimensionTableDao.queryList(buildWrapper(query).orderByDesc("update_time"))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    public List<DimensionTableVO> getNoneList(String keyword) {
        DimensionTableDTO dto = new DimensionTableDTO();
        dto.setKeyword(keyword);
        return all(dto);
    }

    public List<OptionVO> getSelectOption() {
        return all(new DimensionTableDTO()).stream().map(item -> {
            OptionVO option = new OptionVO();
            option.setLabel(item.getCnName());
            option.setValue(item.getId());
            return option;
        }).collect(Collectors.toList());
    }

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
            DimensionTablePO snapshot = objectMapper.readValue(version.getVersionData(), DimensionTablePO.class);
            snapshot.setId(id);
            snapshot.setProjectCode(null);
            snapshot.initUpdate();
            snapshot.setUpdateBy("sys");
            restoreSnapshot(snapshot, id);
            saveVersion(dimensionTableDao.queryById(id));
            return toVO(dimensionTableDao.queryById(id));
        } catch (Exception ex) {
            throw new IllegalStateException("维度建模版本回滚失败", ex);
        }
    }

    public List<DimensionTableFieldVO> listFields(String tableId) {
        requireTable(tableId);
        return dimensionTableFieldDao.queryList(new QueryWrapper<DimensionTableFieldPO>()
                        .eq("table_id", tableId).orderByAsc("create_time"))
                .stream().map(this::toFieldVO).collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public DimensionTableFieldVO createField(String tableId, DimensionTableFieldDTO dto) {
        requireTable(tableId);
        if (dto == null) throw new IllegalArgumentException("字段参数不能为空");
        DimensionTableFieldPO po = toFieldPO(dto, tableId);
        po.setId(null);
        po.setProjectCode(null);
        po.initInsert();
        dimensionTableFieldDao.insert(po);
        return toFieldVO(po);
    }

    @Transactional(rollbackFor = Exception.class)
    public DimensionTableFieldVO updateField(String tableId, String fieldId, DimensionTableFieldDTO dto) {
        requireTable(tableId);
        if (dto == null) throw new IllegalArgumentException("字段参数不能为空");
        DimensionTableFieldPO existing = dimensionTableFieldDao.queryOne(new QueryWrapper<DimensionTableFieldPO>()
                .eq("id", fieldId).eq("table_id", tableId));
        if (existing == null) throw new IllegalArgumentException("维度字段不存在或不属于当前维度表");

        DimensionTableFieldPO po = toFieldPO(dto, tableId);
        po.setId(fieldId);
        po.setProjectCode(null);
        po.setCreateTime(existing.getCreateTime());
        po.setCreateBy(existing.getCreateBy());
        po.initUpdate();
        po.setUpdateBy("sys");
        updateFieldColumns(po, tableId, fieldId);
        return toFieldVO(dimensionTableFieldDao.queryById(fieldId));
    }

    @Transactional(rollbackFor = Exception.class)
    public List<DimensionTableFieldVO> replaceFields(String tableId, List<DimensionTableFieldDTO> fields) {
        requireTable(tableId);
        List<DimensionTableFieldPO> existing = dimensionTableFieldDao.queryList(
                new QueryWrapper<DimensionTableFieldPO>().eq("table_id", tableId));
        Map<String, DimensionTableFieldPO> existingById = new HashMap<>();
        for (DimensionTableFieldPO item : existing) existingById.put(item.getId(), item);

        Set<String> retainedIds = new HashSet<>();
        if (fields != null) {
            for (DimensionTableFieldDTO dto : fields) {
                if (dto == null) continue;
                if (notBlank(dto.getId())) {
                    DimensionTableFieldPO old = existingById.get(dto.getId());
                    if (old == null) throw new IllegalArgumentException("字段ID不属于当前维度表: " + dto.getId());
                    DimensionTableFieldPO po = toFieldPO(dto, tableId);
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
        return listFields(tableId);
    }

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
            wrapper.and(q -> q.like("cn_name", dto.getKeyword()).or().like("table_name", dto.getKeyword()));
        }
        return wrapper;
    }

    private void normalizeRelations(DimensionTableDTO dto) {
        if (dto.getBusProcessIds() != null && dto.getBusProcessIds().size() >= 2) {
            dto.setDomainId(dto.getBusProcessIds().get(0));
            dto.setBusProcessId(dto.getBusProcessIds().get(1));
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
            } else {
                dto.setMartId(id);
                dto.setSubjectId(null);
            }
        }
    }

    private void validateWarehouseLayer(String dwhId) {
        if (!notBlank(dwhId)) throw new IllegalArgumentException("数仓分层不能为空");
        DwLayerVO layer = dwLayerService.selectById(dwhId);
        if (layer == null) throw new IllegalArgumentException("数仓分层不存在或不属于当前项目");
    }

    private void validateUniqueFields(String tableName, String cnName, String excludeId) {
        if (notBlank(tableName)) {
            QueryWrapper<DimensionTablePO> wrapper = new QueryWrapper<DimensionTablePO>().eq("table_name", tableName);
            if (notBlank(excludeId)) wrapper.ne("id", excludeId);
            if (dimensionTableDao.queryCount(wrapper) > 0) {
                throw new IllegalArgumentException("英文表名已存在：" + tableName);
            }
        }
        if (notBlank(cnName)) {
            QueryWrapper<DimensionTablePO> wrapper = new QueryWrapper<DimensionTablePO>().eq("cn_name", cnName);
            if (notBlank(excludeId)) wrapper.ne("id", excludeId);
            if (dimensionTableDao.queryCount(wrapper) > 0) {
                throw new IllegalArgumentException("中文名称已存在：" + cnName);
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
            if (layer.getDwModelType() != null) vo.setDwhName(layer.getDwModelType().getCode());
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
            VersionManagementPO version = new VersionManagementPO();
            version.setSourceTableId(po.getId());
            version.setVersionType(VERSION_TYPE);
            version.setVersionNumber(currentVersion(po.getId()) + 1);
            version.setVersionData(objectMapper.writeValueAsString(po));
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

    private DimensionTableFieldPO toFieldPO(DimensionTableFieldDTO dto, String tableId) {
        DimensionTableFieldPO po = new DimensionTableFieldPO();
        BeanUtils.copyProperties(dto, po);
        po.setTableId(tableId);
        po.setCanNull(dto.getNotNull() == null ? null : !dto.getNotNull());
        po.setProjectCode(null);
        return po;
    }

    private DimensionTableFieldVO toFieldVO(DimensionTableFieldPO po) {
        DimensionTableFieldVO vo = new DimensionTableFieldVO();
        BeanUtils.copyProperties(po, vo);
        vo.setNotNull(po.getCanNull() == null ? null : !po.getCanNull());
        return vo;
    }

    private void updateFieldColumns(DimensionTableFieldPO po, String tableId, String fieldId) {
        UpdateWrapper<DimensionTableFieldPO> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", fieldId).eq("table_id", tableId)
                .set("field_name", po.getFieldName())
                .set("cn_name", po.getCnName())
                .set("data_type", po.getDataType())
                .set("length", po.getLength())
                .set("precision", po.getPrecision())
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

    private int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
