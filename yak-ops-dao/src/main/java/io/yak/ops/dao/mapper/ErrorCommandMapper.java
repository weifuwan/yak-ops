package io.yak.ops.dao.mapper;

import io.yak.ops.dao.entity.CommandCount;
import io.yak.ops.dao.entity.ErrorCommand;

import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface ErrorCommandMapper extends BaseMapper<ErrorCommand> {

    /**
     * count command state
     * @param startTime startTime
     * @param endTime endTime
     * @param projectCodes projectCodes
     * @return CommandCount list
     */
    List<CommandCount> countCommandState(
                                         @Param("startTime") Date startTime,
                                         @Param("endTime") Date endTime,
                                         @Param("projectCodes") List<Long> projectCodes);

    IPage<ErrorCommand> queryErrorCommandPage(Page<ErrorCommand> page);

    IPage<ErrorCommand> queryErrorCommandPageByIds(Page<ErrorCommand> page,
                                                   @Param("workflowDefinitionCodes") List<Long> workflowDefinitionCodes);
}
