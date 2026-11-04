package io.yak.ops.dao.mapper;

import static com.google.common.truth.Truth.assertThat;

import io.yak.ops.common.enums.CommandType;
import io.yak.ops.dao.BaseDaoTest;
import io.yak.ops.dao.entity.CommandCount;
import io.yak.ops.dao.entity.ErrorCommand;
import io.yak.ops.dao.entity.WorkflowDefinition;

import java.util.Date;
import java.util.List;

import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public class ErrorCommandMapperTest extends BaseDaoTest {

    @Autowired
    private ErrorCommandMapper errorCommandMapper;

    @Autowired
    private WorkflowDefinitionMapper workflowDefinitionMapper;

    /**
     * insert
     * @return ErrorCommand
     */
    private ErrorCommand insertOne() {
        // insertOne
        ErrorCommand errorCommand = new ErrorCommand();
        errorCommand.setId(10101);
        errorCommand.setCommandType(CommandType.START_PROCESS);
        errorCommand.setUpdateTime(new Date());
        errorCommand.setStartTime(new Date());
        errorCommandMapper.insert(errorCommand);
        return errorCommand;
    }

    @Test
    public void testQueryCommandPageByIds() {
        ErrorCommand expectedCommand = insertOne();
        Page<ErrorCommand> page = new Page<>(1, 10);
        IPage<ErrorCommand> commandIPage = errorCommandMapper.queryErrorCommandPageByIds(page,
                Lists.newArrayList(expectedCommand.getWorkflowDefinitionCode()));
        List<ErrorCommand> commandList = commandIPage.getRecords();
        assertThat(commandList).isNotEmpty();
        assertThat(commandIPage.getTotal()).isEqualTo(1);
        assertThat(commandList.get(0).getId()).isEqualTo(expectedCommand.getId());
    }

    /**
     * test query
     */
    @Test
    public void testQuery() {
        ErrorCommand errorCommand = insertOne();

        WorkflowDefinition workflowDefinition = new WorkflowDefinition();
        workflowDefinition.setCode(1L);
        workflowDefinition.setName("def 1");
        workflowDefinition.setProjectCode(1010L);
        workflowDefinition.setUserId(101);
        workflowDefinition.setUpdateTime(new Date());
        workflowDefinition.setCreateTime(new Date());
        workflowDefinitionMapper.insert(workflowDefinition);

        errorCommand.setWorkflowDefinitionCode(workflowDefinition.getCode());
        errorCommandMapper.updateById(errorCommand);

        List<CommandCount> commandCounts = errorCommandMapper.countCommandState(
                null,
                null,
                Lists.newArrayList(0L));

        List<CommandCount> commandCounts2 = errorCommandMapper.countCommandState(
                null,
                null,
                Lists.newArrayList(workflowDefinition.getProjectCode(), 200L));

        Assertions.assertEquals(0, commandCounts.size());
        Assertions.assertNotEquals(0, commandCounts2.size());
    }
}
