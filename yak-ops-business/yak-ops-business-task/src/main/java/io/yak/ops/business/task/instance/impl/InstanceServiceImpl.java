package io.yak.ops.business.task.instance.impl;

import io.yak.ops.business.task.instance.InstanceService;
import io.yak.ops.common.bean.dto.task.InstanceQueryDTO;
import io.yak.ops.common.bean.vo.task.AttemptVO;
import io.yak.ops.common.bean.vo.task.InstanceVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.page.PagingData;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.common.util.StringUtils;
import io.yak.ops.dao.entity.task.AttemptEntity;
import io.yak.ops.dao.entity.task.InstanceEntity;
import io.yak.ops.dao.repository.task.AttemptRepository;
import io.yak.ops.dao.repository.task.InstancePageQuery;
import io.yak.ops.dao.repository.task.InstanceRepository;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * 只从通用实例与Attempt表读取历史，先校验Workspace对根实例的可见性。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Service
public class InstanceServiceImpl implements InstanceService {

    @Resource
    private InstanceRepository instanceRepository;

    @Resource
    private AttemptRepository attemptRepository;

    @Override
    public InstanceVO query(String id) {
        return toVO(requireInstance(id));
    }

    @Override
    public PagingData<InstanceVO> queryPage(InstanceQueryDTO dto) {
        if (dto == null || dto.getPageNo() == null || dto.getPageSize() == null
                || dto.getPageNo() < 1 || dto.getPageSize() < 1 || dto.getPageSize() > 100) {
            throw new BusinessException(CommonErrorCode.PARAM_NOT_VALID);
        }
        String type = StringUtils.trimToNull(dto.getTaskType());
        if (type != null) type = type.toUpperCase(Locale.ROOT).replace('-', '_');
        InstancePageQuery query = new InstancePageQuery(
                dto.getPageNo(),
                dto.getPageSize(),
                StringUtils.trimToNull(dto.getTaskId()),
                type,
                dto.getStatus());
        return PagingData.from(instanceRepository
                .queryPage(WorkspaceContext.requireWorkspaceId(), query)
                .map(this::toVO));
    }

    @Override
    public List<AttemptVO> queryAttempts(String instanceId) {
        InstanceEntity instance = requireInstance(instanceId);
        return attemptRepository.queryByInstance(instance.getWorkspaceId(), instance.getId()).stream()
                .map(this::toAttemptVO)
                .toList();
    }

    private InstanceEntity requireInstance(String id) {
        return instanceRepository
                .queryById(WorkspaceContext.requireWorkspaceId(), id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private InstanceVO toVO(InstanceEntity entity) {
        InstanceVO result = BeanCopyUtils.copy(entity, InstanceVO.class, "status", "triggerType");
        result.setStatus(entity.getStatus() == null ? null : entity.getStatus().name());
        result.setTriggerType(entity.getTriggerType() == null ? null : entity.getTriggerType().name());
        return result;
    }

    private AttemptVO toAttemptVO(AttemptEntity attempt) {
        AttemptVO result = BeanCopyUtils.copy(attempt, AttemptVO.class, "status");
        result.setStatus(attempt.getStatus() == null ? null : attempt.getStatus().name());
        result.setLogAvailable(StringUtils.isNotBlank(attempt.getLogUri()));
        return result;
    }
}
