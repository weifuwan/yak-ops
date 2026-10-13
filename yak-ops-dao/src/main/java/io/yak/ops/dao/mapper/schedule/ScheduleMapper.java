package io.yak.ops.dao.mapper.schedule;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.schedule.ScheduleEntity;
import org.apache.ibatis.annotations.Mapper;

/** Maps schedule rows; scoped access and mutation rules live in the repository. */
@Mapper
public interface ScheduleMapper extends BaseMapper<ScheduleEntity> {}
