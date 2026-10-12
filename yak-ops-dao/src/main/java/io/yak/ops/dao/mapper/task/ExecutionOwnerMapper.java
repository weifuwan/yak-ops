package io.yak.ops.dao.mapper.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.task.ExecutionOwnerEntity;
import org.apache.ibatis.annotations.Mapper;

/** Maps the single local executor owner record. */
@Mapper
public interface ExecutionOwnerMapper extends BaseMapper<ExecutionOwnerEntity> {}
