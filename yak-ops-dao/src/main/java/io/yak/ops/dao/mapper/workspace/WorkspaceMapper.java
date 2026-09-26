package io.yak.ops.dao.mapper.workspace;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.workspace.WorkspaceEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Workspace 持久化 Mapper。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Mapper
public interface WorkspaceMapper extends BaseMapper<WorkspaceEntity> {}
