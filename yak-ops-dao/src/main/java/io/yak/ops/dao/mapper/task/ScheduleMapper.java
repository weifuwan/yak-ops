package io.yak.ops.dao.mapper.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.yak.ops.dao.entity.task.ScheduleEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通用Task/Workflow目标调度的MyBatis查询映射。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Mapper
public interface ScheduleMapper extends BaseMapper<ScheduleEntity> {}
