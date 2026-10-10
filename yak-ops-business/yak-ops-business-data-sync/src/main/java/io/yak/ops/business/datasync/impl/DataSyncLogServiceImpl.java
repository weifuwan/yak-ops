package io.yak.ops.business.datasync.impl;

import io.yak.ops.business.datasync.DataSyncLogService;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.bean.vo.datasync.DataSyncExecutionEventVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.DataSyncExecutionEventEntity;
import io.yak.ops.dao.repository.datasync.DataSyncExecutionEventRepository;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 对历史DATA_SYNC执行事件按Workspace与根实例校验后查询。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Service
public class DataSyncLogServiceImpl implements DataSyncLogService {

    @Resource
    private DataSyncInstanceRepository instanceRepository;

    @Resource
    private DataSyncExecutionEventRepository executionEventRepository;

    @Override
    public List<DataSyncExecutionEventVO> queryExecutionEvents(String instanceId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        if (StringUtils.isBlank(instanceId)
                || instanceRepository.queryById(workspaceId, instanceId).isEmpty()) {
            throw new DataSyncException(DataSyncErrorCode.INSTANCE_NOT_FOUND);
        }
        return executionEventRepository.queryByExecution(workspaceId, instanceId).stream()
                .map(this::toVO)
                .toList();
    }

    private DataSyncExecutionEventVO toVO(DataSyncExecutionEventEntity entity) {
        DataSyncExecutionEventVO result =
                BeanCopyUtils.copy(entity, DataSyncExecutionEventVO.class, "level", "eventType");
        result.setLevel(entity.getLevel() == null ? null : entity.getLevel().name());
        result.setEventType(entity.getEventType() == null ? null : entity.getEventType().name());
        return result;
    }
}
