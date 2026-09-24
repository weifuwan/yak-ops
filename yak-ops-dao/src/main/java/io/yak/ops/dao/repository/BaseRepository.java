package io.yak.ops.dao.repository;

import io.yak.framework.common.PageData;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

/**
 * Repository 通用基础能力。
 *
 * @param <T> 持久化实体类型
 * @param <ID> 主键类型
 */
public interface BaseRepository<T, ID extends Serializable> {

    /**
     * 新增数据。
     */
    T add(T entity);

    /**
     * 根据 ID 删除数据。
     */
    int deleteById(ID id);

    /**
     * 更新数据。
     */
    T update(T entity);

    /**
     * 根据 ID 查询数据。
     */
    Optional<T> queryById(ID id);

    /**
     * 查询全部数据。
     */
    List<T> queryList();

    /**
     * 查询数据总数。
     */
    long queryCount();

    /**
     * 简单分页查询。
     */
    PageData<T> queryPage(long pageNo, long pageSize);
}
