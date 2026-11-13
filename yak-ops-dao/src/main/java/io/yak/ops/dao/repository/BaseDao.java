package io.yak.ops.dao.repository;

import org.apache.commons.collections4.CollectionUtils;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.NonNull;

public abstract class BaseDao<ENTITY, MYBATIS_MAPPER extends BaseMapper<ENTITY>> implements IDao<ENTITY> {

    protected MYBATIS_MAPPER mybatisMapper;

    public BaseDao(@NonNull MYBATIS_MAPPER mybatisMapper) {
        this.mybatisMapper = mybatisMapper;
    }

    @Override
    public ENTITY queryById(@NonNull Serializable id) {
        return mybatisMapper.selectById(id);
    }

    @Override
    public Optional<ENTITY> queryOptionalById(@NonNull Serializable id) {
        return Optional.ofNullable(queryById(id));
    }

    @Override
    public List<ENTITY> queryByIds(Collection<? extends Serializable> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }
        return mybatisMapper.selectBatchIds(ids);
    }

    @Override
    public List<ENTITY> queryList(Wrapper<ENTITY> wrapper) {
        return mybatisMapper.selectList(wrapper);
    }

    @Override
    public IPage<ENTITY> queryPage(IPage<ENTITY> page, Wrapper<ENTITY> wrapper) {
        return mybatisMapper.selectPage(page, wrapper);
    }

    @Override
    public ENTITY queryOne(Wrapper<ENTITY> wrapper) {
        return mybatisMapper.selectOne(wrapper);
    }

    @Override
    public long queryCount(Wrapper<ENTITY> wrapper) {
        return mybatisMapper.selectCount(wrapper);
    }

    @Override
    public int update(ENTITY entity, Wrapper<ENTITY> wrapper) {
        return mybatisMapper.update(entity, wrapper);
    }

    @Override
    public int deleteByWrapper(Wrapper<ENTITY> wrapper) {
        return mybatisMapper.delete(wrapper);
    }

    @Override
    public List<ENTITY> queryAll() {
        return mybatisMapper.selectList(null);
    }

    @Override
    public List<ENTITY> queryByCondition(ENTITY queryCondition) {
        if (queryCondition == null) {
            throw new IllegalArgumentException("queryCondition can not be null");
        }
        return mybatisMapper.selectList(new QueryWrapper<>(queryCondition));
    }

    @Override
    public int insert(@NonNull ENTITY model) {
        return mybatisMapper.insert(model);
    }

    @Override
    public void insertBatch(Collection<ENTITY> models) {
        if (CollectionUtils.isEmpty(models)) {
            return;
        }
        for (ENTITY model : models) {
            insert(model);
        }
    }

    @Override
    public boolean updateById(@NonNull ENTITY model) {
        return mybatisMapper.updateById(model) > 0;
    }

    @Override
    public boolean deleteById(@NonNull Serializable id) {
        return mybatisMapper.deleteById(id) > 0;
    }

    @Override
    public boolean deleteByIds(Collection<? extends Serializable> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return true;
        }
        return mybatisMapper.deleteBatchIds(ids) > 0;
    }

    @Override
    public boolean deleteByCondition(ENTITY queryCondition) {
        if (queryCondition == null) {
            throw new IllegalArgumentException("queryCondition can not be null");
        }
        return mybatisMapper.delete(new QueryWrapper<>(queryCondition)) > 0;
    }

}
