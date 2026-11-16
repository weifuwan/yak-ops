package io.yak.ops.modeling.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.service.BaseServiceImpl;
import io.yak.ops.dao.entity.ModelingBasePO;
import io.yak.ops.dao.entity.StandardDirectoryPO;
import io.yak.ops.dao.repository.modeling.ModelingAssetDao;
import io.yak.ops.modeling.api.dto.pagination.ModelingQueryDTO;
import io.yak.ops.modeling.api.vo.ModelingBaseVO;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapperImpl;

/**
 * Shared persistence mechanics for a single, explicitly configured modeling resource.
 * Business routing and resource-specific validation stay in the concrete service.
 */
public abstract class AbstractModelingAssetService<
        PO extends ModelingBasePO, D extends ModelingQueryDTO, V extends ModelingBaseVO>
        extends BaseServiceImpl
        implements io.yak.ops.modeling.application.service.ModelingCrudService<D, V> {

    @Resource
    protected ModelingAssetDao modelingAssetDao;

    protected abstract String resourceKey();

    protected abstract Class<PO> entityClass();

    protected abstract Class<D> dtoClass();

    protected abstract Class<V> viewClass();

    protected abstract QueryWrapper<PO> buildQueryWrapper(D query);

    protected void validate(D dto) {
    }

    protected void beforeCreate(D dto) {
    }

    protected void beforeUpdate(D dto, PO existing) {
    }

    protected void beforeDelete(PO existing) {
    }

    protected void afterCreate(PO created, D dto) {
    }

    protected void afterUpdate(PO updated, D dto) {
    }

    protected void afterDelete(PO deleted) {
    }

    @Override
    public IPage<V> page(D query) {
        D actualQuery = query == null ? newQuery() : query;
        int pageNo = positive(actualQuery.getPageNo(), 1);
        int pageSize = Math.min(positive(actualQuery.getPageSize(), 10), 100);
        IPage<PO> source = modelingAssetDao.queryPage(
                resourceKey(),
                new Page<PO>(pageNo, pageSize),
                buildQueryWrapper(actualQuery).orderByDesc("update_time"));
        Page<V> result = new Page<V>(source.getCurrent(), source.getSize(), source.getTotal());
        result.setRecords(toViews(source.getRecords()));
        return result;
    }

    @Override
    public List<V> list(D query) {
        D actualQuery = query == null ? newQuery() : query;
        return toViews(modelingAssetDao.queryList(
                resourceKey(), buildQueryWrapper(actualQuery).orderByDesc("update_time")));
    }

    @Override
    public V getById(String id) {
        return toView(requireEntity(id));
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public String create(D dto, String operator) {
        if (dto == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        validate(dto);
        beforeCreate(dto);
        PO entity = newEntity();
        BeanUtils.copyProperties(dto, entity);
        entity.setId(null);
        entity.setProjectCode(null);
        entity.initInsert();
        entity.setCreateBy(resolveOperator(operator));
        entity.setUpdateBy(resolveOperator(operator));
        if (modelingAssetDao.insert(resourceKey(), entity) <= 0) {
            throw new IllegalStateException("创建记录失败");
        }
        afterCreate(entity, dto);
        return entity.getId();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public V update(String id, D dto, String operator) {
        if (dto == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (!notBlank(id)) {
            throw new IllegalArgumentException("主键ID不能为空");
        }
        PO existing = requireEntity(id);
        dto.setId(id);
        validate(dto);
        beforeUpdate(dto, existing);
        PO entity = newEntity();
        BeanUtils.copyProperties(dto, entity);
        entity.setId(id);
        entity.setProjectCode(null);
        entity.initUpdate();
        entity.setUpdateBy(resolveOperator(operator));
        modelingAssetDao.updateById(resourceKey(), entity);
        afterUpdate(entity, dto);
        return getById(id);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public String delete(String id) {
        PO existing = requireEntity(id);
        beforeDelete(existing);
        if (modelingAssetDao.deleteById(resourceKey(), id) <= 0) {
            throw new IllegalArgumentException("记录不存在或已被删除");
        }
        afterDelete(existing);
        return id;
    }

    protected PO requireEntity(String id) {
        if (!notBlank(id)) {
            throw new IllegalArgumentException("主键ID不能为空");
        }
        PO entity = modelingAssetDao.queryById(resourceKey(), id);
        if (entity == null) {
            throw new IllegalArgumentException("记录不存在或不属于当前项目");
        }
        return entity;
    }

    protected D newDto() {
        return org.springframework.beans.BeanUtils.instantiateClass(dtoClass());
    }

    protected PO newEntity() {
        return org.springframework.beans.BeanUtils.instantiateClass(entityClass());
    }

    protected V toView(PO entity) {
        V view = org.springframework.beans.BeanUtils.instantiateClass(viewClass());
        BeanUtils.copyProperties(entity, view);
        return view;
    }

    protected List<V> toViews(List<PO> entities) {
        List<V> result = new ArrayList<V>(entities.size());
        for (PO entity : entities) {
            result.add(toView(entity));
        }
        return result;
    }

    protected void addKeyword(QueryWrapper<PO> wrapper, String keyword, String... columns) {
        if (!notBlank(keyword) || columns == null || columns.length == 0) {
            return;
        }
        wrapper.and(condition -> {
            condition.like(columns[0], keyword);
            for (int i = 1; i < columns.length; i++) {
                condition.or().like(columns[i], keyword);
            }
        });
    }

    protected void eqIfNotBlank(QueryWrapper<PO> wrapper, String value, String column) {
        if (notBlank(value)) {
            wrapper.eq(column, value);
        }
    }

    protected void eqIfPresent(QueryWrapper<PO> wrapper, Object value, String column) {
        if (value != null && !value.toString().trim().isEmpty()) {
            wrapper.eq(column, value);
        }
    }

    protected void requireFields(Object source, String... fields) {
        BeanWrapperImpl bean = new BeanWrapperImpl(source);
        for (String field : fields) {
            Object value = bean.isReadableProperty(field) ? bean.getPropertyValue(field) : null;
            if (!notBlank(value == null ? null : String.valueOf(value))) {
                throw new IllegalArgumentException(field + "不能为空");
            }
        }
    }

    protected void validateUniqueFields(String key, Object source, String excludeId) {
        BeanWrapperImpl bean = new BeanWrapperImpl(source);
        validateUniqueField(key, "en_short_name", value(bean, "enShortName"), "英文缩写", excludeId);
        validateUniqueField(key, "en_name", value(bean, "enName"), "英文名称", excludeId);
        validateUniqueField(key, "cn_name", value(bean, "cnName"), "中文名称", excludeId);
    }

    protected void validateUniqueField(
            String key, String column, String value, String label, String excludeId) {
        if (!notBlank(value)) {
            return;
        }
        QueryWrapper<PO> wrapper = new QueryWrapper<PO>().eq(column, value);
        if (notBlank(excludeId)) {
            wrapper.ne("id", excludeId);
        }
        if (modelingAssetDao.queryCount(key, wrapper) > 0) {
            throw new IllegalArgumentException(label + "已存在：" + value);
        }
    }

    protected void validateDirectoryModule(String directoryId, String expectedModule) {
        if (!notBlank(directoryId)) {
            return;
        }
        StandardDirectoryPO directory = modelingAssetDao.queryById("standard-directory", directoryId);
        if (directory == null || !expectedModule.equalsIgnoreCase(directory.getDirModule())) {
            throw new IllegalArgumentException("所选目录不属于当前标准模块");
        }
    }

    protected void validateOptionalReference(String key, String id, String message) {
        if (notBlank(id) && modelingAssetDao.queryById(key, id) == null) {
            throw new IllegalArgumentException(message);
        }
    }

    protected void requireExists(String key, String id, String message) {
        if (!notBlank(id) || modelingAssetDao.queryById(key, id) == null) {
            throw new IllegalArgumentException(message);
        }
    }

    protected String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    protected boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    protected int positive(Integer value, int fallback) {
        return value == null || value.intValue() <= 0 ? fallback : value.intValue();
    }

    protected String resolveOperator(String operator) {
        return notBlank(operator) ? operator : "sys";
    }

    private String value(BeanWrapperImpl bean, String property) {
        return bean.isReadableProperty(property) ? text(bean.getPropertyValue(property)) : "";
    }
}
