package io.yak.ops.dao.repository;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.NonNull;

public interface IDao<Entity> {

    /**
     * Query the entity by primary key.
     */
    Entity queryById(@NonNull Serializable id);

    /**
     * Same with {@link #queryById(Serializable)} but return {@link Optional} instead of null.
     */
    Optional<Entity> queryOptionalById(@NonNull Serializable id);

    /**
     * Query the entity by primary keys.
     */
    List<Entity> queryByIds(Collection<? extends Serializable> ids);

    /**
     * Query entities with a MyBatis-Plus wrapper.
     */
    List<Entity> queryList(Wrapper<Entity> wrapper);

    /**
     * Query a page of entities with a MyBatis-Plus wrapper.
     */
    IPage<Entity> queryPage(IPage<Entity> page, Wrapper<Entity> wrapper);

    /**
     * Query one entity with a MyBatis-Plus wrapper.
     */
    Entity queryOne(Wrapper<Entity> wrapper);

    /**
     * Count entities with a MyBatis-Plus wrapper.
     */
    long queryCount(Wrapper<Entity> wrapper);

    /**
     * Update an entity with a MyBatis-Plus wrapper.
     */
    int update(Entity entity, Wrapper<Entity> wrapper);

    /**
     * Delete entities with a MyBatis-Plus wrapper.
     */
    int deleteByWrapper(Wrapper<Entity> wrapper);

    /**
     * Query all entities.
     */
    List<Entity> queryAll();

    /**
     * Query the entity by condition.
     */
    List<Entity> queryByCondition(Entity queryCondition);

    /**
     * Insert the entity.
     */
    int insert(@NonNull Entity model);

    /**
     * Insert the entities.
     */
    void insertBatch(Collection<Entity> models);

    /**
     * Update the entity by primary key.
     */
    boolean updateById(@NonNull Entity model);

    /**
     * Delete the entity by primary key.
     */
    boolean deleteById(@NonNull Serializable id);

    /**
     * Delete the entities by primary keys.
     */
    boolean deleteByIds(Collection<? extends Serializable> ids);

    /**
     * Delete the entities by condition.
     */
    boolean deleteByCondition(Entity queryCondition);

}
