package io.yak.ops.dao.repository.datasync.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.DataSyncExecutionEventEntity;
import io.yak.ops.dao.mapper.datasync.DataSyncExecutionEventMapper;
import io.yak.ops.dao.repository.datasync.DataSyncExecutionEventRepository;
import io.yak.ops.dao.repository.impl.BaseRepositoryImpl;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * 使用 MyBatis-Plus 实现 Data Sync Execution 产品事件写入与时间线查询。
 *
 * @author weifuwan
 * @since 2026-09-30
 */
@Repository
public class DataSyncExecutionEventRepositoryImpl
        extends BaseRepositoryImpl<DataSyncExecutionEventMapper, DataSyncExecutionEventEntity>
        implements DataSyncExecutionEventRepository {

    @Resource
    private DataSyncExecutionEventMapper eventMapper;

    @Override
    protected DataSyncExecutionEventMapper mapper() {
        return eventMapper;
    }

    @Override
    public List<DataSyncExecutionEventEntity> queryByExecution(String workspaceId, String executionId) {
        if (StringUtils.isBlank(workspaceId) || StringUtils.isBlank(executionId)) return List.of();
        return eventMapper.selectList(Wrappers.<DataSyncExecutionEventEntity>lambdaQuery()
                .eq(DataSyncExecutionEventEntity::getWorkspaceId, workspaceId)
                .eq(DataSyncExecutionEventEntity::getExecutionId, executionId)
                .orderByAsc(DataSyncExecutionEventEntity::getCreateTime)
                .orderByAsc(DataSyncExecutionEventEntity::getId));
    }
}
