package io.yak.ops.dao.mapper.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通用 Task Definition 的 MyBatis 映射。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Mapper
public interface DefinitionMapper extends BaseMapper<DefinitionEntity> {}
