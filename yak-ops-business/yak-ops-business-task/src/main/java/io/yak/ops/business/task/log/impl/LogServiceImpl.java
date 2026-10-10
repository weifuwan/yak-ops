package io.yak.ops.business.task.log.impl;

import io.yak.ops.business.task.log.LogService;
import io.yak.ops.common.bean.vo.task.EventVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.task.EventRepository;
import io.yak.ops.dao.repository.task.InstanceRepository;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 先校验根实例对当前Workspace可见，再返回有序的生命周期事件。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Service
public class LogServiceImpl implements LogService {

    @Resource
    private InstanceRepository instanceRepository;

    @Resource
    private EventRepository eventRepository;

    @Override
    public List<EventVO> queryEvents(String instanceId) {
        String workspaceId = WorkspaceContext.requireWorkspaceId();
        InstanceEntity root = instanceRepository
                .queryById(workspaceId, instanceId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
        return eventRepository.queryByInstance(workspaceId, root.getId()).stream()
                .map(event -> BeanCopyUtils.copy(event, EventVO.class))
                .toList();
    }
}
