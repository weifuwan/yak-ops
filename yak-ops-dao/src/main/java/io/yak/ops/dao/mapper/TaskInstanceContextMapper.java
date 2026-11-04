package io.yak.ops.dao.mapper;

import io.yak.ops.common.enums.ContextType;
import io.yak.ops.dao.entity.TaskInstanceContext;

import org.apache.ibatis.annotations.Param;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface TaskInstanceContextMapper extends BaseMapper<TaskInstanceContext> {

    List<TaskInstanceContext> queryListByTaskInstanceIdAndContextType(@Param("taskInstanceId") int taskInstanceId,
                                                                      @Param("contextType") ContextType contextType);

    int deleteByTaskInstanceIdAndContextType(@Param("taskInstanceId") int taskInstanceId,
                                             @Param("contextType") ContextType contextType);

    List<TaskInstanceContext> batchQueryByTaskInstanceIdsAndContextType(@Param("taskInstanceIds") List<Integer> taskInstanceIds,
                                                                        @Param("contextType") ContextType contextType);
}
