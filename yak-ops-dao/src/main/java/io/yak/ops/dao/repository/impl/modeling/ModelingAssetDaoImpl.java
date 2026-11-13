package io.yak.ops.dao.repository.impl.modeling;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.dao.entity.*;
import io.yak.ops.dao.mapper.*;
import io.yak.ops.dao.repository.modeling.ModelingAssetDao;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ModelingAssetDaoImpl implements ModelingAssetDao {

    private final Map<String, BaseMapper<?>> mappers = new HashMap<>();

    public ModelingAssetDaoImpl(
            @NonNull StandardDirectoryMapper standardDirectoryMapper,
            @NonNull FieldStandardMapper fieldStandardMapper,
            @NonNull CodeStandardMapper codeStandardMapper,
            @NonNull CodeDetailMapper codeDetailMapper,
            @NonNull UnitStandardMapper unitStandardMapper,
            @NonNull DictionaryStandardMapper dictionaryStandardMapper,
            @NonNull AtomIndicatorMapper atomIndicatorMapper,
            @NonNull DerivativeIndicatorMapper derivativeIndicatorMapper,
            @NonNull ComposeIndicatorMapper composeIndicatorMapper,
            @NonNull ModifierMapper modifierMapper,
            @NonNull TimePeriodCategoryMapper timePeriodCategoryMapper,
            @NonNull TimePeriodMapper timePeriodMapper,
            @NonNull VersionManagementMapper versionManagementMapper) {
        mappers.put("standard-directory", standardDirectoryMapper);
        mappers.put("field-standard", fieldStandardMapper);
        mappers.put("code-standard", codeStandardMapper);
        mappers.put("code-detail", codeDetailMapper);
        mappers.put("unit-standard", unitStandardMapper);
        mappers.put("dictionary-standard", dictionaryStandardMapper);
        mappers.put("atom-indicator", atomIndicatorMapper);
        mappers.put("derivative-indicator", derivativeIndicatorMapper);
        mappers.put("compose-indicator", composeIndicatorMapper);
        mappers.put("modifier", modifierMapper);
        mappers.put("time-period-category", timePeriodCategoryMapper);
        mappers.put("time-period", timePeriodMapper);
        mappers.put("version-management", versionManagementMapper);
    }

    @SuppressWarnings("unchecked")
    private <T> BaseMapper<T> mapper(String resource) {
        BaseMapper<?> mapper = mappers.get(resource);
        if (mapper == null) {
            throw new IllegalArgumentException("Unsupported modeling asset resource: " + resource);
        }
        return (BaseMapper<T>) mapper;
    }

    @Override
    public <T> IPage<T> queryPage(String resource, IPage<T> page, Wrapper<T> wrapper) {
        return mapper(resource).selectPage(page, wrapper);
    }

    @Override
    public <T> List<T> queryList(String resource, Wrapper<T> wrapper) {
        return mapper(resource).selectList(wrapper);
    }

    @Override
    public <T> T queryById(String resource, Serializable id) {
        return mapper(resource).selectById(id);
    }

    @Override
    public <T> T queryOne(String resource, Wrapper<T> wrapper) {
        return mapper(resource).selectOne(wrapper);
    }

    @Override
    public <T> long queryCount(String resource, Wrapper<T> wrapper) {
        return mapper(resource).selectCount(wrapper);
    }

    @Override
    public <T> int insert(String resource, T entity) {
        return mapper(resource).insert(entity);
    }

    @Override
    public <T> int updateById(String resource, T entity) {
        return mapper(resource).updateById(entity);
    }

    @Override
    public <T> int update(String resource, T entity, Wrapper<T> wrapper) {
        return mapper(resource).update(entity, wrapper);
    }

    @Override
    public <T> int deleteById(String resource, Serializable id) {
        return mapper(resource).deleteById(id);
    }

    @Override
    public <T> int deleteByWrapper(String resource, Wrapper<T> wrapper) {
        return mapper(resource).delete(wrapper);
    }
}
