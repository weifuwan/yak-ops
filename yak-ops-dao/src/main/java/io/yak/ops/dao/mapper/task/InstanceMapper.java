package io.yak.ops.dao.mapper.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.task.InstanceEntity;
import org.apache.ibatis.annotations.Mapper;

/** Maps Workspace-owned Task Instance records. */
@Mapper
public interface InstanceMapper extends BaseMapper<InstanceEntity> {}
