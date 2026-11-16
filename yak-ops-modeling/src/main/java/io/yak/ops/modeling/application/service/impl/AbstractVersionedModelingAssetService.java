package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.dao.entity.ModelingBasePO;
import io.yak.ops.dao.entity.VersionManagementPO;
import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import io.yak.ops.modeling.api.vo.ModelingAssetVersionVO;
import io.yak.ops.modeling.api.vo.ModelingBaseVO;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.transaction.annotation.Transactional;

/** Shared submit, version history and rollback behavior for versioned modeling assets. */
public abstract class AbstractVersionedModelingAssetService<
        PO extends ModelingBasePO, D extends ModelingQueryDTO, V extends ModelingBaseVO>
        extends AbstractModelingAssetService<PO, D, V> {

    @Resource
    protected ObjectMapper objectMapper;

    protected abstract String versionType();

    protected void beforeSubmit(D dto, PO current) {
    }

    protected void beforeRollback(D dto, PO snapshot) {
    }

    @Transactional(rollbackFor = Exception.class)
    public V submit(String id, String operator) {
        PO current = requireEntity(id);
        D dto = newDto();
        BeanUtils.copyProperties(current, dto);
        validate(dto);
        beforeSubmit(dto, current);
        BeanWrapperImpl bean = new BeanWrapperImpl(current);
        if (bean.isWritableProperty("submit")) {
            bean.setPropertyValue("submit", Boolean.TRUE);
        }
        current.initUpdate();
        current.setUpdateBy(resolveOperator(operator));
        modelingAssetDao.updateById(resourceKey(), current);
        saveVersion(current);
        return getById(id);
    }

    public List<ModelingAssetVersionVO> listVersions(String id) {
        List<VersionManagementPO> records = modelingAssetDao.queryList(
                "version-management",
                new QueryWrapper<VersionManagementPO>()
                        .eq("source_table_id", id)
                        .eq("version_type", versionType())
                        .orderByDesc("version_number"));
        List<ModelingAssetVersionVO> result = new ArrayList<ModelingAssetVersionVO>(records.size());
        for (VersionManagementPO record : records) {
            ModelingAssetVersionVO view = new ModelingAssetVersionVO();
            BeanUtils.copyProperties(record, view);
            result.add(view);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public V rollback(String id, Integer versionNumber, String operator) {
        VersionManagementPO version = modelingAssetDao.queryOne(
                "version-management",
                new QueryWrapper<VersionManagementPO>()
                        .eq("source_table_id", id)
                        .eq("version_type", versionType())
                        .eq("version_number", versionNumber));
        if (version == null) {
            throw new IllegalArgumentException("指定版本不存在");
        }
        try {
            PO snapshot = objectMapper.readValue(version.getVersionData(), entityClass());
            D dto = newDto();
            BeanUtils.copyProperties(snapshot, dto);
            validate(dto);
            beforeRollback(dto, snapshot);
            snapshot.setId(id);
            snapshot.setProjectCode(null);
            snapshot.initUpdate();
            snapshot.setUpdateBy(resolveOperator(operator));
            restoreSnapshot(snapshot, id);
            saveVersion(snapshot);
            return getById(id);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("解析历史版本失败", ex);
        }
    }

    protected void saveVersion(PO entity) {
        try {
            VersionManagementPO latest = modelingAssetDao.queryOne(
                    "version-management",
                    new QueryWrapper<VersionManagementPO>()
                            .eq("source_table_id", entity.getId())
                            .eq("version_type", versionType())
                            .orderByDesc("version_number")
                            .last("limit 1"));
            VersionManagementPO version = new VersionManagementPO();
            version.setSourceTableId(entity.getId());
            version.setVersionType(versionType());
            version.setVersionNumber(latest == null || latest.getVersionNumber() == null
                    ? 1 : latest.getVersionNumber() + 1);
            version.setVersionData(objectMapper.writeValueAsString(entity));
            version.setProjectCode(null);
            version.initInsert();
            modelingAssetDao.insert("version-management", version);
        } catch (Exception ex) {
            throw new IllegalStateException("保存指标版本失败", ex);
        }
    }

    protected void restoreSnapshot(PO snapshot, String id) throws IllegalAccessException {
        UpdateWrapper<PO> wrapper = new UpdateWrapper<PO>().eq("id", id);
        Class<?> current = snapshot.getClass();
        while (current != null && ModelingBasePO.class.isAssignableFrom(current)) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                String name = field.getName();
                if ("id".equals(name) || "projectCode".equals(name)) {
                    continue;
                }
                field.setAccessible(true);
                wrapper.set(toSnakeCase(name), field.get(snapshot));
            }
            if (current == ModelingBasePO.class) {
                break;
            }
            current = current.getSuperclass();
        }
        modelingAssetDao.update(resourceKey(), null, wrapper);
    }

    private String toSnakeCase(String value) {
        StringBuilder result = new StringBuilder();
        for (char character : value.toCharArray()) {
            if (Character.isUpperCase(character)) {
                result.append('_').append(Character.toLowerCase(character));
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }
}
