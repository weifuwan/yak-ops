package io.yak.ops.business.task.schedule.impl;

import io.yak.ops.business.task.schedule.ScheduleService;
import io.yak.ops.common.bean.vo.task.ScheduleVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.enums.task.ScheduleTargetType;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.dao.entity.task.ScheduleEntity;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.ScheduleRepository;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * Task Schedule的通用查询，校验Definition归属，Quartz仍由现有调度服务管理。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Service
public class ScheduleServiceImpl implements ScheduleService {

    @Resource
    private DefinitionRepository definitionRepository;

    @Resource
    private ScheduleRepository scheduleRepository;

    @Override
    public ScheduleVO queryTaskSchedule(String taskId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        definitionRepository
                .queryById(workspaceId, taskId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        return scheduleRepository.queryByTarget(workspaceId, ScheduleTargetType.TASK, taskId)
                .map(this::toVO)
                .orElse(null);
    }

    private ScheduleVO toVO(ScheduleEntity source) {
        ScheduleVO target = BeanCopyUtils.copy(source, ScheduleVO.class, "targetType");
        target.setTargetType(source.getTargetType().name());
        return target;
    }
}
