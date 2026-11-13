package io.yak.ops.dao.repository.impl.modeling;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import io.yak.ops.dao.repository.IDao;
import io.yak.ops.dao.repository.modeling.*;
import lombok.NonNull;
import org.springframework.stereotype.Repository;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resource-key router for the data-standard and indicator asset DAOs.
 * All SQL execution remains inside the individual DAO implementations.
 */
@Repository
public class ModelingAssetDaoImpl implements ModelingAssetDao {

    private final Map<String, IDao<?>> daos = new HashMap<>();

    public ModelingAssetDaoImpl(
            @NonNull StandardDirectoryDao standardDirectoryDao,
            @NonNull FieldStandardDao fieldStandardDao,
            @NonNull CodeStandardDao codeStandardDao,
            @NonNull CodeDetailDao codeDetailDao,
            @NonNull UnitStandardDao unitStandardDao,
            @NonNull DictionaryStandardDao dictionaryStandardDao,
            @NonNull AtomIndicatorDao atomIndicatorDao,
            @NonNull DerivativeIndicatorDao derivativeIndicatorDao,
            @NonNull ComposeIndicatorDao composeIndicatorDao,
            @NonNull ModifierDao modifierDao,
            @NonNull TimePeriodCategoryDao timePeriodCategoryDao,
            @NonNull TimePeriodDao timePeriodDao,
            @NonNull VersionManagementDao versionManagementDao) {
        daos.put("standard-directory", standardDirectoryDao);
        daos.put("field-standard", fieldStandardDao);
        daos.put("code-standard", codeStandardDao);
        daos.put("code-detail", codeDetailDao);
        daos.put("unit-standard", unitStandardDao);
        daos.put("dictionary-standard", dictionaryStandardDao);
        daos.put("atom-indicator", atomIndicatorDao);
        daos.put("derivative-indicator", derivativeIndicatorDao);
        daos.put("compose-indicator", composeIndicatorDao);
        daos.put("modifier", modifierDao);
        daos.put("time-period-category", timePeriodCategoryDao);
        daos.put("time-period", timePeriodDao);
        daos.put("version-management", versionManagementDao);
    }

    @SuppressWarnings("unchecked")
    private <T> IDao<T> dao(String resource) {
        IDao<?> dao = daos.get(resource);
        if (dao == null) {
            throw new IllegalArgumentException("Unsupported modeling asset resource: " + resource);
        }
        return (IDao<T>) dao;
    }

    @Override
    public <T> IPage<T> queryPage(String resource, IPage<T> page, Wrapper<T> wrapper) {
        return dao(resource).queryPage(page, wrapper);
    }

    @Override
    public <T> List<T> queryList(String resource, Wrapper<T> wrapper) {
        return dao(resource).queryList(wrapper);
    }

    @Override
    public <T> T queryById(String resource, Serializable id) {
        return dao(resource).queryById(id);
    }

    @Override
    public <T> T queryOne(String resource, Wrapper<T> wrapper) {
        return dao(resource).queryOne(wrapper);
    }

    @Override
    public <T> long queryCount(String resource, Wrapper<T> wrapper) {
        return dao(resource).queryCount(wrapper);
    }

    @Override
    public <T> int insert(String resource, T entity) {
        return dao(resource).insert(entity);
    }

    @Override
    public <T> int updateById(String resource, T entity) {
        return dao(resource).updateById(entity) ? 1 : 0;
    }

    @Override
    public <T> int update(String resource, T entity, Wrapper<T> wrapper) {
        return dao(resource).update(entity, wrapper);
    }

    @Override
    public <T> int deleteById(String resource, Serializable id) {
        return dao(resource).deleteById(id) ? 1 : 0;
    }

    @Override
    public <T> int deleteByWrapper(String resource, Wrapper<T> wrapper) {
        return dao(resource).deleteByWrapper(wrapper);
    }
}
