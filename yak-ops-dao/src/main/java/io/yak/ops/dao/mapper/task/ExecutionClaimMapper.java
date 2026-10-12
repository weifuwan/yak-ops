package io.yak.ops.dao.mapper.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.task.ExecutionClaimEntity;
import org.apache.ibatis.annotations.Mapper;

/** Maps exclusively claimed execution resources. */
@Mapper
public interface ExecutionClaimMapper extends BaseMapper<ExecutionClaimEntity> {}
