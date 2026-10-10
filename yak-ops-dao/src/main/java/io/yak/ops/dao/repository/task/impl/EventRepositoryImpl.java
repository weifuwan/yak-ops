package io.yak.ops.dao.repository.task.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.task.EventEntity;
import io.yak.ops.dao.mapper.task.EventMapper;
import io.yak.ops.dao.repository.task.EventRepository;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 按Workspace及实例读取单一Task Event表中的历史产品事件。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Repository
public class EventRepositoryImpl implements EventRepository {

    @Resource
    private EventMapper eventMapper;

    @Override
    public List<EventEntity> queryByInstance(String workspaceId, String instanceId) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(instanceId)) return List.of();
        return eventMapper.selectList(Wrappers.<EventEntity>lambdaQuery()
                .eq(EventEntity::getWorkspaceId, workspaceId)
                .eq(EventEntity::getInstanceId, instanceId)
                .orderByAsc(EventEntity::getCreateTime)
                .orderByAsc(EventEntity::getId));
    }
}
