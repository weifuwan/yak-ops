package io.yak.ops.dao.mapper.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.task.AttemptEntity;
import org.apache.ibatis.annotations.Mapper;

/** Maps durable Task Attempt submissions. */
@Mapper
public interface AttemptMapper extends BaseMapper<AttemptEntity> {}
