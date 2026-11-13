package io.yak.ops.dao.repository.modeling;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.io.Serializable;
import java.util.List;

/** Persistence boundary for the dynamic data-standard and indicator resources. */
public interface ModelingAssetDao {

    <T> IPage<T> queryPage(String resource, IPage<T> page, Wrapper<T> wrapper);

    <T> List<T> queryList(String resource, Wrapper<T> wrapper);

    <T> T queryById(String resource, Serializable id);

    <T> T queryOne(String resource, Wrapper<T> wrapper);

    <T> long queryCount(String resource, Wrapper<T> wrapper);

    <T> int insert(String resource, T entity);

    <T> int updateById(String resource, T entity);

    <T> int update(String resource, T entity, Wrapper<T> wrapper);

    <T> int deleteById(String resource, Serializable id);

    <T> int deleteByWrapper(String resource, Wrapper<T> wrapper);
}
