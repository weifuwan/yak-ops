package io.yak.ops.dao.repository.task.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.common.enums.task.ScheduleTargetType;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.task.ScheduleEntity;
import io.yak.ops.dao.mapper.task.ScheduleMapper;
import io.yak.ops.dao.repository.task.ScheduleRepository;
import jakarta.annotation.Resource;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 通用Schedule目标身份解析；不把Quartz运行态作为持久化来源。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Repository
public class ScheduleRepositoryImpl implements ScheduleRepository {

    @Resource
    private ScheduleMapper scheduleMapper;

    @Override
    public Optional<ScheduleEntity> queryByTarget(
            String workspaceId, ScheduleTargetType type, String targetId) {
        if (StringUtils.isBlank(workspaceId) || type == null || StringUtils.isBlank(targetId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(scheduleMapper.selectOne(Wrappers.<ScheduleEntity>lambdaQuery()
                .eq(ScheduleEntity::getWorkspaceId, workspaceId)
                .eq(ScheduleEntity::getTargetType, type)
                .eq(ScheduleEntity::getTargetId, targetId)));
    }
}
