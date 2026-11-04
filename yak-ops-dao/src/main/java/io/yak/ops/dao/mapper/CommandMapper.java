package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.entity.CommandCount;

import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface CommandMapper extends BaseMapper<Command> {

    /**
     * count command state
     *
     * @param startTime    startTime
     * @param endTime      endTime
     * @param projectCodes projectCodes
     * @return CommandCount list
     */
    List<CommandCount> countCommandState(
                                         @Param("startTime") Date startTime,
                                         @Param("endTime") Date endTime,
                                         @Param("projectCodes") List<Long> projectCodes);

    /**
     * query command page
     *
     * @return
     */
    IPage<Command> queryCommandPage(Page<Command> page);

    List<Command> queryCommandByIdSlot(@Param("currentSlotIndex") int currentSlotIndex,
                                       @Param("totalSlot") int totalSlot,
                                       @Param("idStep") int idStep,
                                       @Param("fetchNumber") int fetchNum);

    void deleteByWorkflowInstanceIds(@Param("workflowInstanceIds") List<Integer> workflowInstanceIds);

    IPage<Command> queryCommandPageByIds(Page<Command> page,
                                         @Param("workflowDefinitionCodes") List<Long> workflowDefinitionCodes);
}
