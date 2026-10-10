package io.yak.ops.dao.repository.task.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.task.AttemptEntity;
import io.yak.ops.dao.mapper.task.AttemptMapper;
import io.yak.ops.dao.repository.task.AttemptRepository;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 基于单一Attempt物理表获取历史；不修改Task的业务执行状态。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Repository
public class AttemptRepositoryImpl implements AttemptRepository {

    @Resource
    private AttemptMapper attemptMapper;

    @Override
    public List<AttemptEntity> queryByInstance(String workspaceId, String instanceId) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(instanceId)) return List.of();
        return attemptMapper.selectList(Wrappers.<AttemptEntity>lambdaQuery()
                .eq(AttemptEntity::getWorkspaceId, workspaceId)
                .eq(AttemptEntity::getInstanceId, instanceId)
                .orderByAsc(AttemptEntity::getAttemptNo));
    }
}
