package io.yak.ops.dao.repository.task.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.yak.ops.common.page.PageData;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.mapper.task.InstanceMapper;
import io.yak.ops.dao.repository.task.InstancePageQuery;
import io.yak.ops.dao.repository.task.InstanceRepository;
import jakarta.annotation.Resource;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * 读取同一张通用Task Instance表，保留既有DATA_SYNC实例ID与历史状态。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Repository
public class InstanceRepositoryImpl implements InstanceRepository {

    @Resource
    private InstanceMapper instanceMapper;

    @Override
    public Optional<InstanceEntity> queryById(String workspaceId, String instanceId) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(instanceId)) return Optional.empty();
        return Optional.ofNullable(instanceMapper.selectOne(Wrappers.<InstanceEntity>lambdaQuery()
                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                .eq(InstanceEntity::getId, instanceId)));
    }

    @Override
    public PageData<InstanceEntity> queryPage(String workspaceId, InstancePageQuery query) {
        if (StringUtils.isBlank(workspaceId) || query == null) throw new IllegalArgumentException("Invalid query");
        LambdaQueryWrapper<InstanceEntity> filter = Wrappers.<InstanceEntity>lambdaQuery()
                .eq(InstanceEntity::getWorkspaceId, workspaceId)
                .eq(StringUtils.isNotBlank(query.taskId()), InstanceEntity::getTaskId, query.taskId())
                .eq(StringUtils.isNotBlank(query.taskType()), InstanceEntity::getTaskType, query.taskType())
                .eq(query.status() != null, InstanceEntity::getStatus, query.status())
                .orderByDesc(InstanceEntity::getCreateTime)
                .orderByDesc(InstanceEntity::getId);
        Page<InstanceEntity> page = Page.of(Math.max(1, query.pageNo()), Math.max(1, query.pageSize()));
        IPage<InstanceEntity> records = instanceMapper.selectPage(page, filter);
        return new PageData<>(
                records.getRecords(), records.getTotal(), records.getPages(), records.getCurrent(), records.getSize());
    }
}
