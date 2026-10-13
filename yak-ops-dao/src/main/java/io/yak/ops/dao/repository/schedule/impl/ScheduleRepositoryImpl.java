package io.yak.ops.dao.repository.schedule.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.enums.schedule.ScheduleTargetType;
import io.yak.ops.common.page.PageData;
import io.yak.ops.dao.entity.schedule.ScheduleEntity;
import io.yak.ops.dao.mapper.schedule.ScheduleMapper;
import io.yak.ops.dao.repository.schedule.SchedulePageQuery;
import io.yak.ops.dao.repository.schedule.ScheduleRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Stores schedule intent with Workspace, deletion, and revision guards on every mutation. */
@Repository
public class ScheduleRepositoryImpl implements ScheduleRepository {
    @Resource
    private ScheduleMapper mapper;

    @Override
    public void add(ScheduleEntity entity) {
        mapper.insert(entity);
    }

    @Override
    public Optional<ScheduleEntity> queryById(String workspaceId, String id) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<ScheduleEntity>lambdaQuery()
                .eq(ScheduleEntity::getWorkspaceId, workspaceId)
                .eq(ScheduleEntity::getId, id)
                .isNull(ScheduleEntity::getDeletedAt)));
    }

    @Override
    public Optional<ScheduleEntity> queryByIdForUpdate(String workspaceId, String id) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<ScheduleEntity>lambdaQuery()
                .eq(ScheduleEntity::getWorkspaceId, workspaceId)
                .eq(ScheduleEntity::getId, id)
                .isNull(ScheduleEntity::getDeletedAt)
                .last("FOR UPDATE")));
    }

    @Override
    public Optional<ScheduleEntity> queryByTarget(String workspaceId, ScheduleTargetType type, String id) {
        return Optional.ofNullable(mapper.selectOne(Wrappers.<ScheduleEntity>lambdaQuery()
                .eq(ScheduleEntity::getWorkspaceId, workspaceId)
                .eq(ScheduleEntity::getTargetType, type)
                .eq(ScheduleEntity::getTargetId, id)
                .isNull(ScheduleEntity::getDeletedAt)));
    }

    @Override
    public PageData<ScheduleEntity> queryPage(SchedulePageQuery query) {
        var page = mapper.selectPage(
                new Page<>(query.pageNo(), query.pageSize()),
                Wrappers.<ScheduleEntity>lambdaQuery()
                        .eq(ScheduleEntity::getWorkspaceId, query.workspaceId())
                        .isNull(ScheduleEntity::getDeletedAt)
                        .eq(query.targetType() != null, ScheduleEntity::getTargetType, query.targetType())
                        .eq(query.targetId() != null, ScheduleEntity::getTargetId, query.targetId())
                        .eq(query.enabled() != null, ScheduleEntity::isEnabled, query.enabled())
                        .orderByDesc(ScheduleEntity::getUpdateTime, ScheduleEntity::getId));
        return new PageData<>(page.getRecords(), page.getTotal(), page.getPages(), page.getCurrent(), page.getSize());
    }

    @Override
    public boolean updateDisabled(ScheduleEntity entity, long expectedRevision) {
        return mapper.update(
                        null,
                        Wrappers.<ScheduleEntity>lambdaUpdate()
                                .eq(ScheduleEntity::getWorkspaceId, entity.getWorkspaceId())
                                .eq(ScheduleEntity::getId, entity.getId())
                                .isNull(ScheduleEntity::getDeletedAt)
                                .eq(ScheduleEntity::isEnabled, false)
                                .eq(ScheduleEntity::getRevision, expectedRevision)
                                .set(ScheduleEntity::getCronExpression, entity.getCronExpression())
                                .set(ScheduleEntity::getTimeZone, entity.getTimeZone())
                                .set(ScheduleEntity::getRevision, entity.getRevision())
                                .set(ScheduleEntity::getUpdateTime, entity.getUpdateTime())
                                .set(ScheduleEntity::getUpdateBy, entity.getUpdateBy()))
                == 1;
    }

    @Override
    public boolean softDelete(String workspaceId, String id, long expectedRevision, LocalDateTime at, String operator) {
        return mapper.update(
                        null,
                        Wrappers.<ScheduleEntity>lambdaUpdate()
                                .eq(ScheduleEntity::getWorkspaceId, workspaceId)
                                .eq(ScheduleEntity::getId, id)
                                .isNull(ScheduleEntity::getDeletedAt)
                                .eq(ScheduleEntity::getRevision, expectedRevision)
                                .set(ScheduleEntity::isEnabled, false)
                                .set(ScheduleEntity::getDeletedAt, at)
                                .set(ScheduleEntity::getRevision, Math.addExact(expectedRevision, 1))
                                .set(ScheduleEntity::getUpdateTime, at)
                                .set(ScheduleEntity::getUpdateBy, operator))
                == 1;
    }

    @Override
    public void disableForTarget(
            String workspaceId, ScheduleTargetType type, String id, LocalDateTime at, String operator) {
        mapper.update(
                null,
                Wrappers.<ScheduleEntity>lambdaUpdate()
                        .eq(ScheduleEntity::getWorkspaceId, workspaceId)
                        .eq(ScheduleEntity::getTargetType, type)
                        .eq(ScheduleEntity::getTargetId, id)
                        .isNull(ScheduleEntity::getDeletedAt)
                        .eq(ScheduleEntity::isEnabled, true)
                        .set(ScheduleEntity::isEnabled, false)
                        .setSql("revision = revision + 1")
                        .set(ScheduleEntity::getUpdateTime, at)
                        .set(ScheduleEntity::getUpdateBy, operator));
    }
}
