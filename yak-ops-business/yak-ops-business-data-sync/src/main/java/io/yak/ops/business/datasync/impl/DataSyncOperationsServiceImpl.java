package io.yak.ops.business.datasync.impl;

import io.yak.ops.business.datasync.DataSyncOperationsService;
import io.yak.ops.business.datasync.exception.DataSyncErrorCode;
import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.business.datasync.scheduler.ScheduleEngine;
import io.yak.ops.business.datasync.scheduler.ScheduleEngineException;
import io.yak.ops.common.bean.dto.datasync.DataSyncRetryPolicyDTO;
import io.yak.ops.common.bean.dto.datasync.DataSyncTaskQueryDTO;
import io.yak.ops.common.bean.vo.datasync.DataSyncInstanceVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncRetryPolicyVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncScheduleVO;
import io.yak.ops.common.bean.vo.datasync.DataSyncTaskOperationVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.datasync.DataSyncDesiredState;
import io.yak.ops.common.enums.datasync.DataSyncRetryPolicyMode;
import io.yak.ops.common.enums.datasync.DataSyncTaskStatus;
import io.yak.ops.common.enums.datasync.DataSyncType;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.CollectionUtils;
import io.yak.ops.common.util.JSONUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.datasync.DataSyncInstanceEntity;
import io.yak.ops.dao.entity.datasync.DataSyncScheduleEntity;
import io.yak.ops.dao.entity.datasync.SyncDefinitionEntity;
import io.yak.ops.dao.repository.datasync.DataSyncInstanceRepository;
import io.yak.ops.dao.repository.datasync.DataSyncScheduleRepository;
import io.yak.ops.dao.repository.datasync.SyncDefinitionPageQuery;
import io.yak.ops.dao.repository.datasync.SyncDefinitionRepository;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * DataSyncOperationsService 的业务职责实现。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Service
public class DataSyncOperationsServiceImpl implements DataSyncOperationsService {

    @Resource
    private SyncDefinitionRepository taskRepository;

    @Resource
    private DataSyncInstanceRepository instanceRepository;

    @Resource
    private DataSyncScheduleRepository scheduleRepository;

    @Resource
    private ScheduleEngine scheduleEngine;

    private static final Logger LOG = LoggerFactory.getLogger(DataSyncOperationsServiceImpl.class);

    @Override
    public PagingData<DataSyncTaskOperationVO> queryTaskOperationPage(DataSyncTaskQueryDTO dto) {
        if (dto == null) throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY);
        if (CollectionUtils.isNotEmpty(dto.getSorts())) {
            throw new DataSyncException(DataSyncErrorCode.INVALID_QUERY, "运维任务分页暂不支持自定义排序");
        }

        String workspaceId = WorkspaceContext.requireWorkspaceId();
        SyncDefinitionPageQuery query = new SyncDefinitionPageQuery(
                dto.getPageNo(),
                dto.getPageSize(),
                StringUtils.trimToNull(dto.getKeyword()),
                dto.getSyncType(),
                DataSyncTaskStatus.PUBLISHED,
                StringUtils.trimToNull(dto.getSourceDataSourceId()),
                StringUtils.trimToNull(dto.getTargetDataSourceId()));
        return PagingData.from(
                taskRepository.queryPage(workspaceId, query).map(task -> toTaskOperationVO(workspaceId, task)));
    }

    private DataSyncTaskOperationVO toTaskOperationVO(String workspaceId, SyncDefinitionEntity task) {
        DataSyncTaskOperationVO target = new DataSyncTaskOperationVO();
        target.setId(task.getId());
        target.setName(task.getName());
        target.setSyncType(
                task.getSyncType() == null ? null : task.getSyncType().name());
        target.setDesiredState(taskDesiredState(task).name());
        target.setDefinitionVersion(task.getDefinitionVersion());
        target.setRetryPolicy(toRetryPolicyVO(task.getRetryPolicy()));
        instanceRepository
                .queryLatestByTask(workspaceId, task.getId())
                .ifPresent(instance -> target.setLatestInstance(toInstanceSummaryVO(instance)));
        if (task.getSyncType() == DataSyncType.OFFLINE) {
            scheduleRepository
                    .queryByTask(workspaceId, task.getId())
                    .ifPresent(schedule -> target.setSchedule(toRuntimeScheduleVO(schedule)));
        }
        return target;
    }

    private DataSyncScheduleVO toScheduleVO(DataSyncScheduleEntity entity) {
        return BeanCopyUtils.copy(entity, DataSyncScheduleVO.class);
    }

    private DataSyncScheduleVO toRuntimeScheduleVO(DataSyncScheduleEntity entity) {
        DataSyncScheduleVO target = toScheduleVO(entity);
        if (!Boolean.TRUE.equals(entity.getEnabled())) return target;
        try {
            scheduleEngine
                    .queryNextFireTime(entity.getId())
                    .ifPresent(nextFireTime -> target.setNextFireTime(
                            LocalDateTime.ofInstant(nextFireTime, ZoneId.of(entity.getTimeZone()))));
        } catch (ScheduleEngineException exception) {
            LOG.warn("查询调度下一次触发时间失败，scheduleId={}, error={}", entity.getId(), exception.getMessage());
        }
        return target;
    }

    private DataSyncDesiredState taskDesiredState(SyncDefinitionEntity task) {
        return task.getDesiredState() == null ? DataSyncDesiredState.STOPPED : task.getDesiredState();
    }

    private DataSyncRetryPolicyDTO retryPolicyConfig(String json) {
        DataSyncRetryPolicyDTO policy = StringUtils.isBlank(json)
                ? new DataSyncRetryPolicyDTO()
                : JSONUtils.parseObject(json, DataSyncRetryPolicyDTO.class);
        normalizeRetryPolicy(policy);
        return policy;
    }

    private void normalizeRetryPolicy(DataSyncRetryPolicyDTO policy) {
        if (policy.getMode() == null) {
            policy.setMode(DataSyncRetryPolicyMode.FIXED);
        }
    }

    private DataSyncRetryPolicyVO toRetryPolicyVO(String json) {
        return BeanCopyUtils.copy(retryPolicyConfig(json), DataSyncRetryPolicyVO.class);
    }

    private DataSyncInstanceVO toInstanceSummaryVO(DataSyncInstanceEntity source) {
        DataSyncInstanceVO target = BeanCopyUtils.copy(
                source, DataSyncInstanceVO.class, "syncType", "triggerType", "status", "definitionSnapshot");
        target.setSyncType(
                source.getSyncType() == null ? null : source.getSyncType().name());
        target.setTriggerType(
                source.getTriggerType() == null ? null : source.getTriggerType().name());
        target.setStatus(source.getStatus() == null ? null : source.getStatus().name());
        return target;
    }
}
