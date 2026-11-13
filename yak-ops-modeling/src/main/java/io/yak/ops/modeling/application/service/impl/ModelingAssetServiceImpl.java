package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.modeling.api.vo.tree.ModelingAssetTreeVO;
import io.yak.ops.modeling.application.service.ModelingAssetService;
import io.yak.ops.common.service.BaseServiceImpl;
import io.yak.ops.dao.entity.*;
import io.yak.ops.dao.repository.modeling.ModelingAssetDao;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** Project-scoped application service for migrated standards and indicator assets. */
@Service
public class ModelingAssetServiceImpl extends BaseServiceImpl implements ModelingAssetService {

    @Resource private ObjectMapper objectMapper;
    @Resource private ModelingAssetDao modelingAssetDao;

    public IPage<Map<String, Object>> page(String resource, Map<String, Object> params) {
        String key = normalize(resource);
        if ("standard-directory".equals(key) && "UNIT".equalsIgnoreCase(text(params.get("dirModule")))) {
            ensureDefaultUnitDirectories();
        }
        if ("time-period-category".equals(key)) ensureDefaultTimePeriodCategories();
        if ("time-period".equals(key)) ensureDefaultTimePeriods();
        switch (key) {
            case "standard-directory": return page("standard-directory", params, key);
            case "field-standard": return page("field-standard", params, key);
            case "code-standard": return page("code-standard", params, key);
            case "code-detail": return page("code-detail", params, key);
            case "unit-standard": return page("unit-standard", params, key);
            case "dictionary-standard": return page("dictionary-standard", params, key);
            case "atom-indicator": return page("atom-indicator", params, key);
            case "derivative-indicator": return page("derivative-indicator", params, key);
            case "compose-indicator": return page("compose-indicator", params, key);
            case "modifier": return page("modifier", params, key);
            case "time-period-category": return page("time-period-category", params, key);
            case "time-period": return page("time-period", params, key);
            default: throw unsupported(resource);
        }
    }

    public List<Map<String, Object>> all(String resource, Map<String, Object> params) {
        String key = normalize(resource);
        if ("standard-directory".equals(key) && "UNIT".equalsIgnoreCase(text(params.get("dirModule")))) {
            ensureDefaultUnitDirectories();
        }
        if ("time-period-category".equals(key)) ensureDefaultTimePeriodCategories();
        if ("time-period".equals(key)) ensureDefaultTimePeriods();
        switch (key) {
            case "standard-directory": return all("standard-directory", params, key);
            case "field-standard": return all("field-standard", params, key);
            case "code-standard": return all("code-standard", params, key);
            case "code-detail": return all("code-detail", params, key);
            case "unit-standard": return all("unit-standard", params, key);
            case "dictionary-standard": return all("dictionary-standard", params, key);
            case "atom-indicator": return all("atom-indicator", params, key);
            case "derivative-indicator": return all("derivative-indicator", params, key);
            case "compose-indicator": return all("compose-indicator", params, key);
            case "modifier": return all("modifier", params, key);
            case "time-period-category": return all("time-period-category", params, key);
            case "time-period": return all("time-period", params, key);
            default: throw unsupported(resource);
        }
    }

    public Map<String, Object> get(String resource, String id) {
        String key = normalize(resource);
        switch (key) {
            case "standard-directory": return detail("standard-directory", id);
            case "field-standard": return detail("field-standard", id);
            case "code-standard": {
                Map<String, Object> result = detail("code-standard", id);
                result.put("detailList", all("code-detail",
                        Collections.<String, Object>singletonMap("codeId", id), "code-detail"));
                return result;
            }
            case "code-detail": return detail("code-detail", id);
            case "unit-standard": return detail("unit-standard", id);
            case "dictionary-standard": return detail("dictionary-standard", id);
            case "atom-indicator": return detail("atom-indicator", id);
            case "derivative-indicator": return detail("derivative-indicator", id);
            case "compose-indicator": return detail("compose-indicator", id);
            case "modifier": return detail("modifier", id);
            case "time-period-category": return detail("time-period-category", id);
            case "time-period": return detail("time-period", id);
            default: throw unsupported(resource);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public String create(String resource, Map<String, Object> payload) {
        String key = normalize(resource);
        Map<String, Object> values = safePayload(payload);
        validatePayload(key, values);
        switch (key) {
            case "standard-directory":
                validateDirectoryParent(null, text(values.get("parentId")), text(values.get("dirModule")));
                validateDirectoryUnique(values, null);
                return create("standard-directory", StandardDirectoryPO.class, values).getId();
            case "field-standard":
                validateDirectoryModule(text(values.get("parentId")), "FIELD");
                return create("field-standard", FieldStandardPO.class, values).getId();
            case "code-standard": {
                validateDirectoryModule(text(values.get("parentId")), "CODE");
                Object details = values.remove("detailList");
                CodeStandardPO po = create("code-standard", CodeStandardPO.class, values);
                saveCodeDetails(po.getId(), details);
                return po.getId();
            }
            case "code-detail":
                requireExists("code-standard", text(values.get("codeId")), "标准代码不存在");
                return create("code-detail", CodeDetailPO.class, values).getId();
            case "unit-standard":
                validateDirectoryModule(text(values.get("parentId")), "UNIT");
                return create("unit-standard", UnitStandardPO.class, values).getId();
            case "dictionary-standard":
                return create("dictionary-standard", DictionaryStandardPO.class, values).getId();
            case "atom-indicator":
                validateUniqueFields("atom-indicator", values, null);
                validateOptionalReference("unit-standard", text(values.get("unitId")), "度量单位不存在");
                return createDraft("atom-indicator", AtomIndicatorPO.class, values).getId();
            case "derivative-indicator":
                validateUniqueFields("derivative-indicator", values, null);
                validateOptionalReference("atom-indicator", text(values.get("atomicId")), "原子指标不存在");
                validateOptionalReference("time-period", text(values.get("timePeriodId")), "时间周期不存在");
                return createDraft("derivative-indicator", DerivativeIndicatorPO.class, values).getId();
            case "compose-indicator":
                validateUniqueFields("compose-indicator", values, null);
                validateOptionalReference("derivative-indicator", text(values.get("deriveId")), "派生指标不存在");
                return createDraft("compose-indicator", ComposeIndicatorPO.class, values).getId();
            case "modifier":
                validateUniqueFields("modifier", values, null);
                validateOptionalReference("code-standard", text(values.get("codeId")), "标准代码不存在");
                validateOptionalReference("code-detail", text(values.get("codeDetailId")), "标准代码明细不存在");
                return createDraft("modifier", ModifierPO.class, values).getId();
            case "time-period-category":
                return create("time-period-category", TimePeriodCategoryPO.class, values).getId();
            case "time-period":
                validateUniqueFields("time-period", values, null);
                requireExists("time-period-category", text(values.get("categoryId")), "时间周期目录不存在");
                return createDraft("time-period", TimePeriodPO.class, values).getId();
            default: throw unsupported(resource);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> update(String resource, Map<String, Object> payload) {
        String key = normalize(resource);
        Map<String, Object> values = safePayload(payload);
        String id = text(values.get("id"));
        if (id.isEmpty()) throw new IllegalArgumentException("主键ID不能为空");
        validatePayload(key, values);
        switch (key) {
            case "standard-directory":
                validateDirectoryParent(id, text(values.get("parentId")), text(values.get("dirModule")));
                validateDirectoryUnique(values, id);
                return toMap(update("standard-directory", StandardDirectoryPO.class, values));
            case "field-standard":
                validateDirectoryModule(text(values.get("parentId")), "FIELD");
                return toMap(update("field-standard", FieldStandardPO.class, values));
            case "code-standard": {
                validateDirectoryModule(text(values.get("parentId")), "CODE");
                Object details = values.remove("detailList");
                CodeStandardPO po = update("code-standard", CodeStandardPO.class, values);
                if (payload != null && payload.containsKey("detailList")) {
                    modelingAssetDao.deleteByWrapper("code-detail", new QueryWrapper<CodeDetailPO>().eq("code_id", id));
                    saveCodeDetails(id, details);
                }
                return get(key, po.getId());
            }
            case "code-detail":
                requireExists("code-standard", text(values.get("codeId")), "标准代码不存在");
                return toMap(update("code-detail", CodeDetailPO.class, values));
            case "unit-standard":
                validateDirectoryModule(text(values.get("parentId")), "UNIT");
                return toMap(update("unit-standard", UnitStandardPO.class, values));
            case "dictionary-standard":
                return toMap(update("dictionary-standard", DictionaryStandardPO.class, values));
            case "atom-indicator":
                validateUniqueFields("atom-indicator", values, id);
                validateOptionalReference("unit-standard", text(values.get("unitId")), "度量单位不存在");
                return updateDraft("atom-indicator", AtomIndicatorPO.class, values);
            case "derivative-indicator":
                validateUniqueFields("derivative-indicator", values, id);
                validateOptionalReference("atom-indicator", text(values.get("atomicId")), "原子指标不存在");
                validateOptionalReference("time-period", text(values.get("timePeriodId")), "时间周期不存在");
                return updateDraft("derivative-indicator", DerivativeIndicatorPO.class, values);
            case "compose-indicator":
                validateUniqueFields("compose-indicator", values, id);
                validateOptionalReference("derivative-indicator", text(values.get("deriveId")), "派生指标不存在");
                return updateDraft("compose-indicator", ComposeIndicatorPO.class, values);
            case "modifier":
                validateUniqueFields("modifier", values, id);
                validateOptionalReference("code-standard", text(values.get("codeId")), "标准代码不存在");
                validateOptionalReference("code-detail", text(values.get("codeDetailId")), "标准代码明细不存在");
                return updateDraft("modifier", ModifierPO.class, values);
            case "time-period-category":
                return toMap(update("time-period-category", TimePeriodCategoryPO.class, values));
            case "time-period":
                validateUniqueFields("time-period", values, id);
                requireExists("time-period-category", text(values.get("categoryId")), "时间周期目录不存在");
                return updateDraft("time-period", TimePeriodPO.class, values);
            default: throw unsupported(resource);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public String delete(String resource, String id) {
        String key = normalize(resource);
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("主键ID不能为空");
        ensureCanDelete(key, id);
        int affected;
        switch (key) {
            case "standard-directory": affected = modelingAssetDao.deleteById("standard-directory", id); break;
            case "field-standard": affected = modelingAssetDao.deleteById("field-standard", id); break;
            case "code-standard":
                modelingAssetDao.deleteByWrapper("code-detail", new QueryWrapper<CodeDetailPO>().eq("code_id", id));
                affected = modelingAssetDao.deleteById("code-standard", id);
                break;
            case "code-detail": affected = modelingAssetDao.deleteById("code-detail", id); break;
            case "unit-standard": affected = modelingAssetDao.deleteById("unit-standard", id); break;
            case "dictionary-standard": affected = modelingAssetDao.deleteById("dictionary-standard", id); break;
            case "atom-indicator":
                affected = modelingAssetDao.deleteById("atom-indicator", id);
                deleteVersions(key, id);
                break;
            case "derivative-indicator":
                affected = modelingAssetDao.deleteById("derivative-indicator", id);
                deleteVersions(key, id);
                break;
            case "compose-indicator":
                affected = modelingAssetDao.deleteById("compose-indicator", id);
                deleteVersions(key, id);
                break;
            case "modifier":
                affected = modelingAssetDao.deleteById("modifier", id);
                deleteVersions(key, id);
                break;
            case "time-period-category": affected = modelingAssetDao.deleteById("time-period-category", id); break;
            case "time-period":
                affected = modelingAssetDao.deleteById("time-period", id);
                deleteVersions(key, id);
                break;
            default: throw unsupported(resource);
        }
        if (affected <= 0) throw new IllegalArgumentException("记录不存在或已被删除");
        return id;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> submit(String resource, String id) {
        String key = normalize(resource);
        String versionType = versionType(key);
        switch (key) {
            case "atom-indicator": return submit("atom-indicator", id, versionType, key);
            case "derivative-indicator": return submit("derivative-indicator", id, versionType, key);
            case "compose-indicator": return submit("compose-indicator", id, versionType, key);
            case "modifier": return submit("modifier", id, versionType, key);
            case "time-period": return submit("time-period", id, versionType, key);
            default: throw new IllegalArgumentException("该资源不支持提交和版本管理");
        }
    }

    public List<Map<String, Object>> versions(String resource, String id) {
        String type = versionType(normalize(resource));
        List<VersionManagementPO> records = modelingAssetDao.queryList("version-management", 
                new QueryWrapper<VersionManagementPO>().eq("source_table_id", id)
                        .eq("version_type", type).orderByDesc("version_number"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (VersionManagementPO record : records) {
            Map<String, Object> item = toMap(record);
            item.remove("versionData");
            result.add(item);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> rollback(String resource, String id, Integer versionNumber) {
        String key = normalize(resource);
        String type = versionType(key);
        VersionManagementPO version = modelingAssetDao.queryOne("version-management", 
                new QueryWrapper<VersionManagementPO>().eq("source_table_id", id)
                        .eq("version_type", type).eq("version_number", versionNumber));
        if (version == null) throw new IllegalArgumentException("指定版本不存在");
        switch (key) {
            case "atom-indicator": return rollback("atom-indicator", id, version);
            case "derivative-indicator": return rollback("derivative-indicator", id, version);
            case "compose-indicator": return rollback("compose-indicator", id, version);
            case "modifier": return rollback("modifier", id, version);
            case "time-period": return rollback("time-period", id, version);
            default: throw new IllegalArgumentException("该资源不支持版本回滚");
        }
    }

    public List<ModelingAssetTreeVO> directoryTree(String module) {
        String type = module == null ? "" : module.trim().toUpperCase(Locale.ROOT);
        if (!Arrays.asList("FIELD", "CODE", "UNIT").contains(type)) {
            throw new IllegalArgumentException("目录模块必须是 FIELD、CODE 或 UNIT");
        }
        if ("UNIT".equals(type)) ensureDefaultUnitDirectories();
        List<ModelingAssetTreeVO> nodes = new ArrayList<>();
        List<StandardDirectoryPO> directories = modelingAssetDao.queryList("standard-directory", 
                new QueryWrapper<StandardDirectoryPO>().eq("dir_module", type).orderByAsc("dir_name"));
        for (StandardDirectoryPO item : directories) {
            nodes.add(node(item.getId(), item.getDirName(), item.getParentId(), item.getDirType()));
        }
        if ("FIELD".equals(type)) {
            for (FieldStandardPO item : modelingAssetDao.queryList("field-standard", new QueryWrapper<FieldStandardPO>().orderByAsc("cn_name"))) {
                nodes.add(node(item.getId(), item.getCnName(), item.getParentId(), "FIELD_STANDARD"));
            }
        } else if ("CODE".equals(type)) {
            for (CodeStandardPO item : modelingAssetDao.queryList("code-standard", new QueryWrapper<CodeStandardPO>().orderByAsc("cn_name"))) {
                nodes.add(node(item.getId(), item.getCnName(), item.getParentId(), "CODE_STANDARD"));
            }
        } else {
            for (UnitStandardPO item : modelingAssetDao.queryList("unit-standard", new QueryWrapper<UnitStandardPO>().orderByAsc("cn_name"))) {
                nodes.add(node(item.getId(), item.getCnName(), item.getParentId(), "UNIT_STANDARD"));
            }
        }
        Map<String, ModelingAssetTreeVO> byId = new LinkedHashMap<>();
        for (ModelingAssetTreeVO item : nodes) byId.put(item.getId(), item);
        List<ModelingAssetTreeVO> roots = new ArrayList<>();
        for (ModelingAssetTreeVO item : nodes) {
            ModelingAssetTreeVO parent = item.getParentId() == null ? null : byId.get(item.getParentId());
            if (parent == null) roots.add(item);
            else parent.getChildren().add(item);
        }
        return roots;
    }

    private <T extends ModelingBasePO> IPage<Map<String, Object>> page(
            String mapperKey, Map<String, Object> params, String resource) {
        int pageNo = positiveInt(params.get("pageNo"), 1);
        int pageSize = Math.min(positiveInt(params.get("pageSize"), 10), 100);
        IPage<T> result = modelingAssetDao.queryPage(mapperKey, new Page<T>(pageNo, pageSize),
                this.<T>buildWrapper(params, resource).orderByDesc("update_time"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (T record : result.getRecords()) rows.add(toMap(record));
        Page<Map<String, Object>> page = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        page.setRecords(rows);
        return page;
    }

    private <T extends ModelingBasePO> List<Map<String, Object>> all(
            String mapperKey, Map<String, Object> params, String resource) {
        List<T> records = modelingAssetDao.queryList(mapperKey, this.<T>buildWrapper(params, resource).orderByDesc("update_time"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (T record : records) rows.add(toMap(record));
        return rows;
    }

    private <T extends ModelingBasePO> Map<String, Object> detail(String mapperKey, String id) {
        T record = modelingAssetDao.queryById(mapperKey, id);
        if (record == null) throw new IllegalArgumentException("记录不存在或不属于当前工作空间");
        return toMap(record);
    }

    private <T extends ModelingBasePO> T create(String mapperKey, Class<T> type, Map<String, Object> values) {
        T po = convert(values, type);
        po.setId(null);
        po.setProjectCode(null);
        po.initInsert();
        modelingAssetDao.insert(mapperKey, po);
        return po;
    }

    private <T extends ModelingBasePO> T update(String mapperKey, Class<T> type, Map<String, Object> values) {
        T po = convert(values, type);
        if (po.getId() == null || modelingAssetDao.queryById(mapperKey, po.getId()) == null) {
            throw new IllegalArgumentException("记录不存在或不属于当前工作空间");
        }
        po.setProjectCode(null);
        po.initUpdate();
        modelingAssetDao.updateById(mapperKey, po);
        return modelingAssetDao.queryById(mapperKey, po.getId());
    }

    private <T extends ModelingBasePO> T createDraft(String mapperKey, Class<T> type, Map<String, Object> values) {
        T po = convert(values, type);
        setIfWritable(po, "submit", Boolean.FALSE);
        po.setId(null);
        po.setProjectCode(null);
        po.initInsert();
        modelingAssetDao.insert(mapperKey, po);
        return po;
    }

    private <T extends ModelingBasePO> Map<String, Object> updateDraft(
            String mapperKey, Class<T> type, Map<String, Object> values) {
        T po = convert(values, type);
        if (po.getId() == null || modelingAssetDao.queryById(mapperKey, po.getId()) == null) {
            throw new IllegalArgumentException("记录不存在或不属于当前工作空间");
        }
        setIfWritable(po, "submit", Boolean.FALSE);
        po.setProjectCode(null);
        po.initUpdate();
        modelingAssetDao.updateById(mapperKey, po);
        return toMap(modelingAssetDao.queryById(mapperKey, po.getId()));
    }

    private <T extends ModelingBasePO> Map<String, Object> submit(
            String mapperKey, String id, String type, String resource) {
        T po = modelingAssetDao.queryById(mapperKey, id);
        if (po == null) throw new IllegalArgumentException("记录不存在或不属于当前工作空间");
        @SuppressWarnings("unchecked")
        Map<String, Object> values = safePayload(objectMapper.convertValue(po, Map.class));
        validatePayload(resource, values);
        setIfWritable(po, "submit", Boolean.TRUE);
        po.initUpdate();
        modelingAssetDao.updateById(mapperKey, po);
        saveVersion(po, type);
        return toMap(modelingAssetDao.queryById(mapperKey, id));
    }

    private <T extends ModelingBasePO> Map<String, Object> rollback(
            String mapperKey, String id, VersionManagementPO version) {
        try {
            T current = modelingAssetDao.queryById(mapperKey, id);
            if (current == null) throw new IllegalArgumentException("指标记录不存在或不属于当前工作空间");
            @SuppressWarnings("unchecked")
            Class<T> type = (Class<T>) current.getClass();
            T snapshot = objectMapper.readValue(version.getVersionData(), type);
            snapshot.setId(id);
            snapshot.setProjectCode(null);
            snapshot.initUpdate();
            snapshot.setUpdateBy("sys");
            restoreSnapshot(mapper, snapshot, id);
            saveVersion(snapshot, version.getVersionType());
            return toMap(modelingAssetDao.queryById(mapperKey, id));
        } catch (Exception ex) {
            throw new IllegalStateException("解析历史版本失败", ex);
        }
    }

    private <T extends ModelingBasePO> void restoreSnapshot(String mapperKey, T snapshot, String id)
            throws IllegalAccessException {
        UpdateWrapper<T> wrapper = new UpdateWrapper<T>().eq("id", id);
        Class<?> current = snapshot.getClass();
        while (current != null && ModelingBasePO.class.isAssignableFrom(current)) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                String name = field.getName();
                if ("id".equals(name) || "projectCode".equals(name)) continue;
                field.setAccessible(true);
                wrapper.set(toSnakeCase(name), field.get(snapshot));
            }
            if (current == ModelingBasePO.class) break;
            current = current.getSuperclass();
        }
        modelingAssetDao.update(mapperKey, null, wrapper);
    }

    private String toSnakeCase(String value) {
        StringBuilder result = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isUpperCase(c)) {
                result.append('_').append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private <T extends ModelingBasePO> void saveVersion(T po, String type) {
        try {
            List<VersionManagementPO> latest = modelingAssetDao.queryList("version-management", 
                    new QueryWrapper<VersionManagementPO>().eq("source_table_id", po.getId())
                            .eq("version_type", type).orderByDesc("version_number").last("limit 1"));
            int next = latest.isEmpty() ? 1 : latest.get(0).getVersionNumber() + 1;
            VersionManagementPO version = new VersionManagementPO();
            version.setSourceTableId(po.getId());
            version.setVersionType(type);
            version.setVersionNumber(next);
            version.setVersionData(objectMapper.writeValueAsString(po));
            version.setProjectCode(null);
            version.initInsert();
            modelingAssetDao.insert("version-management", version);
        } catch (Exception ex) {
            throw new IllegalStateException("保存指标版本失败", ex);
        }
    }

    private <T extends ModelingBasePO> QueryWrapper<T> buildWrapper(Map<String, Object> params, String resource) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        String keyword = text(params.get("keyword"));
        if (keyword.isEmpty()) {
            for (String column : searchColumns(resource)) {
                String value = text(params.get(toCamel(column)));
                if (!value.isEmpty()) { keyword = value; break; }
            }
        }
        if (!keyword.isEmpty()) {
            final String value = keyword;
            String[] columns = searchColumns(resource);
            if (columns.length > 0) {
                wrapper.and(w -> {
                    w.like(columns[0], value);
                    for (int i = 1; i < columns.length; i++) w.or().like(columns[i], value);
                });
            }
        }
        for (Map.Entry<String, String> filter : filterColumns(resource).entrySet()) {
            Object value = params.get(filter.getKey());
            if (value == null || text(value).isEmpty()) continue;
            if ("submit".equals(filter.getKey()) || "leaf".equals(filter.getKey())) {
                wrapper.eq(filter.getValue(), Boolean.valueOf(text(value)));
            } else {
                wrapper.eq(filter.getValue(), value);
            }
        }
        return wrapper;
    }

    private String[] searchColumns(String resource) {
        switch (resource) {
            case "standard-directory": return new String[]{"dir_name"};
            case "field-standard": return new String[]{"cn_name", "en_short_name", "en_name", "std_code"};
            case "code-standard": case "code-detail": case "unit-standard":
            case "dictionary-standard": case "atom-indicator": case "derivative-indicator":
            case "compose-indicator": case "modifier": case "time-period":
                return new String[]{"cn_name", "en_short_name", "en_name"};
            case "time-period-category": return new String[]{"cn_name", "en_name"};
            default: return new String[0];
        }
    }

    private Map<String, String> filterColumns(String resource) {
        Map<String, String> result = new LinkedHashMap<>();
        if ("standard-directory".equals(resource)) {
            result.put("parentId", "parent_id"); result.put("dirModule", "dir_module"); result.put("dirType", "dir_type");
        } else if ("field-standard".equals(resource) || "code-standard".equals(resource)
                || "unit-standard".equals(resource)) {
            result.put("parentId", "parent_id");
        } else if ("code-detail".equals(resource)) {
            result.put("codeId", "code_id");
        } else if ("atom-indicator".equals(resource)) {
            result.put("busTypeId", "bus_type_id"); result.put("domainId", "domain_id");
            result.put("busProcessId", "bus_process_id"); result.put("unitId", "unit_id"); result.put("submit", "submit");
        } else if ("derivative-indicator".equals(resource)) {
            result.put("atomicId", "atomic_id"); result.put("timePeriodId", "time_period_id");
            result.put("busTypeId", "bus_type_id"); result.put("domainId", "domain_id");
            result.put("busProcessId", "bus_process_id"); result.put("submit", "submit");
        } else if ("compose-indicator".equals(resource)) {
            result.put("deriveId", "derive_id"); result.put("busTypeId", "bus_type_id");
            result.put("domainId", "domain_id"); result.put("busProcessId", "bus_process_id"); result.put("submit", "submit");
        } else if ("modifier".equals(resource)) {
            result.put("modifierType", "modifier_type"); result.put("busTypeId", "bus_type_id");
            result.put("domainId", "domain_id"); result.put("submit", "submit");
        } else if ("time-period-category".equals(resource)) {
            result.put("parentId", "parent_id"); result.put("leaf", "leaf");
        } else if ("time-period".equals(resource)) {
            result.put("categoryId", "category_id"); result.put("submit", "submit");
        }
        return result;
    }

    private void validatePayload(String resource, Map<String, Object> values) {
        String required;
        switch (resource) {
            case "standard-directory": required = "dirName"; break;
            case "field-standard": case "code-standard": case "code-detail": case "unit-standard":
            case "dictionary-standard": case "atom-indicator": case "derivative-indicator":
            case "compose-indicator": case "modifier": case "time-period-category": case "time-period":
                required = "cnName"; break;
            default: throw unsupported(resource);
        }
        if (text(values.get(required)).isEmpty()) throw new IllegalArgumentException(required + "不能为空");
        if ("standard-directory".equals(resource)) {
            if (text(values.get("dirModule")).isEmpty()) throw new IllegalArgumentException("目录模块不能为空");
            if (text(values.get("dirType")).isEmpty()) throw new IllegalArgumentException("目录类型不能为空");
            validateDirectoryType(text(values.get("dirModule")), text(values.get("dirType")));
            validateDirectoryModule(text(values.get("parentId")), text(values.get("dirModule")));
        }
        if ("field-standard".equals(resource)) {
            requireFields(values, "stdCode", "enShortName", "enName", "fieldDataType");
        }
        if ("code-standard".equals(resource)) {
            requireFields(values, "enShortName", "enName");
        }
        if ("code-detail".equals(resource)) {
            requireFields(values, "codeId", "enShortName", "enName");
        }
        if ("unit-standard".equals(resource) || "dictionary-standard".equals(resource)) {
            requireFields(values, "enShortName", "enName");
        }
        if ("atom-indicator".equals(resource)) {
            requireFields(values, "enShortName", "businessCaliber", "director");
        }
        if ("derivative-indicator".equals(resource)) {
            requireFields(values, "enShortName", "atomicId", "timePeriodId");
        }
        if ("compose-indicator".equals(resource)) {
            requireFields(values, "enShortName");
        }
        if ("modifier".equals(resource)) {
            requireFields(values, "enShortName", "modifierType", "businessCaliber", "director");
        }
        if ("compose-indicator".equals(resource)) {
            String computeMode = text(values.get("computeMode"));
            if ("0".equals(computeMode)) requireFields(values, "computeExpr");
            else if ("1".equals(computeMode)) requireFields(values, "deriveId", "compareType");
            else throw new IllegalArgumentException("计算模式必须是 0（计算表达式）或 1（同环比）");
        }
        if ("time-period".equals(resource)) {
            requireFields(values, "categoryId", "enShortName", "director");
        }
    }

    private void requireFields(Map<String, Object> values, String... fields) {
        for (String field : fields) {
            if (text(values.get(field)).isEmpty()) {
                throw new IllegalArgumentException(field + "不能为空");
            }
        }
    }

    private void validateDirectoryParent(String id, String parentId, String module) {
        validateDirectoryModule(parentId, module);
        if (parentId.isEmpty()) return;
        Set<String> visited = new HashSet<>();
        String currentId = parentId;
        while (!currentId.isEmpty() && visited.add(currentId)) {
            if (id != null && id.equals(currentId)) {
                throw new IllegalArgumentException("目录不能移动到自身或自己的子目录下");
            }
            StandardDirectoryPO parent = modelingAssetDao.queryById("standard-directory", currentId);
            currentId = parent == null ? "" : text(parent.getParentId());
        }
    }

    private <T extends ModelingBasePO> void validateUniqueFields(
            String mapperKey, Map<String, Object> values, String excludeId) {
        validateUniqueField(mapper, "en_short_name", text(values.get("enShortName")), "英文缩写", excludeId);
        validateUniqueField(mapper, "en_name", text(values.get("enName")), "英文名称", excludeId);
        validateUniqueField(mapper, "cn_name", text(values.get("cnName")), "中文名称", excludeId);
    }

    private <T extends ModelingBasePO> void validateUniqueField(
            String mapperKey, String column, String value, String label, String excludeId) {
        if (value.isEmpty()) return;
        QueryWrapper<T> wrapper = new QueryWrapper<T>().eq(column, value);
        if (excludeId != null) wrapper.ne("id", excludeId);
        if (modelingAssetDao.queryCount(mapperKey, wrapper) > 0) {
            throw new IllegalArgumentException(label + "已存在：" + value);
        }
    }

    private void validateDirectoryType(String module, String dirType) {
        String normalizedModule = module.toUpperCase(Locale.ROOT);
        if (!Arrays.asList("FIELD", "CODE", "UNIT").contains(normalizedModule)) {
            throw new IllegalArgumentException("标准目录模块必须是 FIELD、CODE 或 UNIT");
        }
        Set<String> allowed = new HashSet<>();
        allowed.add("FOLDER");
        if ("FIELD".equals(normalizedModule)) {
            allowed.add("STANDARD_SET");
            allowed.add("STANDARD");
        } else if ("CODE".equals(normalizedModule)) {
            allowed.add("CODE");
        } else {
            allowed.add("UNIT");
        }
        if (!allowed.contains(dirType.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("目录类型与所属标准模块不匹配");
        }
    }

    private void validateDirectoryUnique(Map<String, Object> values, String excludeId) {
        QueryWrapper<StandardDirectoryPO> wrapper = new QueryWrapper<StandardDirectoryPO>()
                .eq("dir_name", text(values.get("dirName")))
                .eq("dir_module", text(values.get("dirModule")));
        if (excludeId != null) wrapper.ne("id", excludeId);
        if (modelingAssetDao.queryCount("standard-directory", wrapper) > 0) {
            throw new IllegalArgumentException("该标准模块下已存在同名目录");
        }
    }

    private void validateDirectoryModule(String directoryId, String expectedModule) {
        if (directoryId.isEmpty()) return;
        StandardDirectoryPO directory = modelingAssetDao.queryById("standard-directory", directoryId);
        if (directory == null || !expectedModule.equalsIgnoreCase(directory.getDirModule())) {
            throw new IllegalArgumentException("所选目录不属于当前标准模块");
        }
    }

    private <T extends ModelingBasePO> void validateOptionalReference(String mapperKey, String id, String message) {
        if (!id.isEmpty() && modelingAssetDao.queryById(mapperKey, id) == null) throw new IllegalArgumentException(message);
    }

    private <T extends ModelingBasePO> void requireExists(String mapperKey, String id, String message) {
        if (id.isEmpty() || modelingAssetDao.queryById(mapperKey, id) == null) throw new IllegalArgumentException(message);
    }

    private void ensureCanDelete(String resource, String id) {
        if ("standard-directory".equals(resource)) {
            if (modelingAssetDao.queryCount("standard-directory", new QueryWrapper<StandardDirectoryPO>().eq("parent_id", id)) > 0
                    || modelingAssetDao.queryCount("field-standard", new QueryWrapper<FieldStandardPO>().eq("parent_id", id)) > 0
                    || modelingAssetDao.queryCount("code-standard", new QueryWrapper<CodeStandardPO>().eq("parent_id", id)) > 0
                    || modelingAssetDao.queryCount("unit-standard", new QueryWrapper<UnitStandardPO>().eq("parent_id", id)) > 0) {
                throw new IllegalArgumentException("目录下仍有子目录或标准记录，请先清空后再删除");
            }
        } else if ("code-standard".equals(resource)) {
            if (modelingAssetDao.queryCount("field-standard", new QueryWrapper<FieldStandardPO>().eq("reference", id)) > 0
                    || modelingAssetDao.queryCount("modifier", new QueryWrapper<ModifierPO>().eq("code_id", id)) > 0) {
                throw new IllegalArgumentException("该标准代码已被字段标准或指标修饰词引用，不能删除");
            }
        } else if ("code-detail".equals(resource)) {
            if (modelingAssetDao.queryCount("modifier", new QueryWrapper<ModifierPO>().eq("code_detail_id", id)) > 0) {
                throw new IllegalArgumentException("该代码明细已被指标修饰词引用，不能删除");
            }
        } else if ("unit-standard".equals(resource)) {
            if (modelingAssetDao.queryCount("atom-indicator", new QueryWrapper<AtomIndicatorPO>().eq("unit_id", id)) > 0) {
                throw new IllegalArgumentException("该度量单位已被原子指标引用，不能删除");
            }
        } else if ("atom-indicator".equals(resource)) {
            if (modelingAssetDao.queryCount("derivative-indicator", new QueryWrapper<DerivativeIndicatorPO>().eq("atomic_id", id)) > 0) {
                throw new IllegalArgumentException("该原子指标已被派生指标引用，不能删除");
            }
        } else if ("derivative-indicator".equals(resource)) {
            if (modelingAssetDao.queryCount("compose-indicator", new QueryWrapper<ComposeIndicatorPO>().eq("derive_id", id)) > 0) {
                throw new IllegalArgumentException("该派生指标已被复合指标引用，不能删除");
            }
        } else if ("modifier".equals(resource)) {
            if (modelingAssetDao.queryCount("derivative-indicator", new QueryWrapper<DerivativeIndicatorPO>().like("modifier_id", id)) > 0) {
                throw new IllegalArgumentException("该修饰词已被派生指标引用，不能删除");
            }
        } else if ("time-period".equals(resource)) {
            if (modelingAssetDao.queryCount("derivative-indicator", new QueryWrapper<DerivativeIndicatorPO>().eq("time_period_id", id)) > 0) {
                throw new IllegalArgumentException("该时间周期已被派生指标引用，不能删除");
            }
        } else if ("time-period-category".equals(resource)) {
            if (modelingAssetDao.queryCount("time-period-category", new QueryWrapper<TimePeriodCategoryPO>().eq("parent_id", id)) > 0
                    || modelingAssetDao.queryCount("time-period", new QueryWrapper<TimePeriodPO>().eq("category_id", id)) > 0) {
                throw new IllegalArgumentException("该周期目录下仍有子目录或时间周期，不能删除");
            }
        }
    }

    private void saveCodeDetails(String codeId, Object value) {
        if (!(value instanceof List)) return;
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> values = safePayload((Map<String, Object>) item);
            CodeDetailPO po = convert(values, CodeDetailPO.class);
            po.setId(null);
            po.setCodeId(codeId);
            po.setProjectCode(null);
            po.initInsert();
            modelingAssetDao.insert("code-detail", po);
        }
    }

    private void ensureDefaultUnitDirectories() {
        Long count = modelingAssetDao.queryCount("standard-directory", 
                new QueryWrapper<StandardDirectoryPO>().eq("dir_module", "UNIT"));
        if (count != null && count > 0) return;
        for (String name : new String[]{"货币单位", "对象量词", "比例单位", "排名单位", "时间单位"}) {
            StandardDirectoryPO item = new StandardDirectoryPO();
            item.setDirName(name); item.setDirType("FOLDER"); item.setDirModule("UNIT");
            item.setProjectCode(null); item.initInsert(); modelingAssetDao.insert("standard-directory", item);
        }
    }

    private void ensureDefaultTimePeriodCategories() {
        Long count = modelingAssetDao.queryCount("time-period-category", new QueryWrapper<TimePeriodCategoryPO>());
        if (count != null && count > 0) return;
        TimePeriodCategoryPO root = new TimePeriodCategoryPO();
        root.setCnName("时间周期"); root.setEnName("TIME_PERIOD"); root.setLeaf(Boolean.FALSE);
        root.setProjectCode(null); root.initInsert(); modelingAssetDao.insert("time-period-category", root);
        String[][] categories = {
                {"天", "DAY"}, {"周", "WEEK"}, {"月", "MONTH"}, {"季度", "QUARTER"},
                {"年", "YEAR"}, {"小时", "HOUR"}, {"分钟", "MINUTE"}, {"秒", "SECOND"}, {"历史全量", "HISTORY"}
        };
        for (String[] category : categories) {
            TimePeriodCategoryPO item = new TimePeriodCategoryPO();
            item.setCnName(category[0]); item.setEnName(category[1]); item.setParentId(root.getId());
            item.setLeaf(Boolean.TRUE); item.setProjectCode(null); item.initInsert();
            modelingAssetDao.insert("time-period-category", item);
        }
    }

    private void ensureDefaultTimePeriods() {
        ensureDefaultTimePeriodCategories();
        Long count = modelingAssetDao.queryCount("time-period", new QueryWrapper<TimePeriodPO>());
        if (count != null && count > 0) return;
        Map<String, String> categoryIds = new HashMap<>();
        for (TimePeriodCategoryPO item : modelingAssetDao.queryList("time-period-category", new QueryWrapper<TimePeriodCategoryPO>())) {
            if (item.getEnName() != null) categoryIds.put(item.getEnName(), item.getId());
        }
        String[][] periods = {
                {"DAY", "1d", "last 1 day", "近1天", "最近1天"},
                {"DAY", "3d", "last 3 day", "近3天", "最近3天"},
                {"WEEK", "1w", "last 7 day", "近7天", "最近7天"},
                {"MONTH", "1m", "last 30 day", "近30天", "最近30天"},
                {"WEEK", "cw", "calendar week", "自然周", "自然周（周一至周日）"},
                {"MONTH", "cm", "calendar month", "自然月", "自然月（每月1日至月末）"},
                {"QUARTER", "cq", "calendar quarter", "自然季度", "自然季度"}
        };
        for (String[] data : periods) {
            String categoryId = categoryIds.get(data[0]);
            if (categoryId == null) continue;
            TimePeriodPO item = new TimePeriodPO();
            item.setCategoryId(categoryId); item.setEnShortName(data[1]); item.setEnName(data[2]);
            item.setCnName(data[3]); item.setRemark(data[4]); item.setSubmit(Boolean.FALSE);
            item.setProjectCode(null); item.initInsert(); modelingAssetDao.insert("time-period", item);
        }
    }

    private ModelingAssetTreeVO node(String id, String title, String parentId, String type) {
        ModelingAssetTreeVO node = new ModelingAssetTreeVO();
        node.setId(id); node.setKey(type + ":" + id); node.setValue(id); node.setTitle(title);
        node.setParentId(parentId); node.setType(type); node.setChildren(new ArrayList<ModelingAssetTreeVO>());
        return node;
    }

    private String versionType(String resource) {
        switch (resource) {
            case "atom-indicator": case "derivative-indicator": case "compose-indicator":
            case "modifier": case "time-period":
                return resource.replace('-', '_').toUpperCase(Locale.ROOT);
            default: throw new IllegalArgumentException("该资源不支持版本管理");
        }
    }

    private <T extends ModelingBasePO> T convert(Map<String, Object> values, Class<T> type) {
        return objectMapper.convertValue(values, type);
    }

    private Map<String, Object> safePayload(Map<String, Object> payload) {
        Map<String, Object> values = payload == null ? new HashMap<String, Object>() : new HashMap<>(payload);
        for (String key : new String[]{"projectCode", "createBy", "createTime", "updateBy", "updateTime",
                "currentVersion", "submitName", "pageNo", "pageSize", "keyword"}) values.remove(key);
        return values;
    }

    private Map<String, Object> toMap(Object value) {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = objectMapper.convertValue(value, Map.class);
        result.remove("projectCode");
        return result;
    }

    private void setIfWritable(Object target, String property, Object value) {
        BeanWrapperImpl wrapper = new BeanWrapperImpl(target);
        if (wrapper.isWritableProperty(property)) wrapper.setPropertyValue(property, value);
    }

    private void deleteVersions(String resource, String id) {
        modelingAssetDao.deleteByWrapper("version-management", new QueryWrapper<VersionManagementPO>()
                .eq("source_table_id", id).eq("version_type", versionType(resource)));
    }

    private String normalize(String resource) {
        return resource == null ? "" : resource.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private IllegalArgumentException unsupported(String resource) {
        return new IllegalArgumentException("不支持的数据建模资源：" + resource);
    }

    private int positiveInt(Object value, int fallback) {
        try { int number = Integer.parseInt(text(value)); return number > 0 ? number : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String toCamel(String column) {
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char c : column.toCharArray()) {
            if (c == '_') upper = true;
            else { result.append(upper ? Character.toUpperCase(c) : c); upper = false; }
        }
        return result.toString();
    }
}
