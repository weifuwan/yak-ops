package io.yak.ops.business.task.definition.impl;

import io.yak.ops.business.task.definition.DefinitionService;
import io.yak.ops.common.bean.vo.task.DefinitionVO;
import io.yak.ops.common.bean.vo.task.DefinitionVersionVO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.enums.common.CommonErrorCode;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.common.util.BeanCopyUtils;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import jakarta.annotation.Resource;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 从通用持久化模型查询当前 Definition 与不可变历史版本。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Service
public class DefinitionServiceImpl implements DefinitionService {

    @Resource
    private DefinitionRepository definitionRepository;

    @Resource
    private DefinitionVersionRepository versionRepository;

    @Override
    public DefinitionVO query(String id) {
        return toVO(requireDefinition(id));
    }

    @Override
    public List<DefinitionVersionVO> queryVersions(String id) {
        DefinitionEntity definition = requireDefinition(id);
        return versionRepository.queryByDefinition(definition.getWorkspaceId(), definition.getId()).stream()
                .map(entity -> BeanCopyUtils.copy(entity, DefinitionVersionVO.class))
                .toList();
    }

    private DefinitionEntity requireDefinition(String id) {
        return definitionRepository
                .queryById(WorkspaceContext.requireWorkspaceId(), id)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_EXISTS));
    }

    private DefinitionVO toVO(DefinitionEntity source) {
        DefinitionVO target = BeanCopyUtils.copy(source, DefinitionVO.class, "status");
        target.setStatus(source.getStatus().name());
        return target;
    }
}
