package io.yak.ops.dao.mapper.task;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * DolphinScheduler TaskGroupMapper's atomic slot CAS contract, adapted only for
 * Yak Ops BIGINT group IDs and the namespaced table.
 *
 * <p>Source: DolphinScheduler dev ee47c371, TaskGroupMapper.xml
 * acquireTaskGroupSlot / releaseTaskGroupSlot.
 */
@Mapper
public interface TaskGroupMapper {
    @Update("UPDATE yak_ops_task_group SET use_size = use_size + 1" + " WHERE id = #{id} AND use_size < group_size")
    int acquireTaskGroupSlot(@Param("id") Long id);

    @Update("UPDATE yak_ops_task_group SET use_size = use_size - 1" + " WHERE id = #{id} AND use_size > 0")
    int releaseTaskGroupSlot(@Param("id") Long id);
}
