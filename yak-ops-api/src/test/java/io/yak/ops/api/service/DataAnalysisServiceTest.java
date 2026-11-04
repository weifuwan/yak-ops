package io.yak.ops.api.service;

import static io.yak.ops.api.AssertionsHelper.assertDoesNotThrow;
import static io.yak.ops.api.AssertionsHelper.assertThrowsServiceException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.yak.ops.api.dto.CommandStateCount;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.permission.ResourcePermissionCheckService;
import io.yak.ops.api.service.impl.DataAnalysisServiceImpl;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.vo.TaskInstanceCountVO;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.AuthorizationType;
import io.yak.ops.common.enums.CommandType;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.Command;
import io.yak.ops.dao.entity.CommandCount;
import io.yak.ops.dao.entity.ErrorCommand;
import io.yak.ops.dao.entity.ExecuteStatusCount;
import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.mapper.CommandMapper;
import io.yak.ops.dao.mapper.ErrorCommandMapper;
import io.yak.ops.dao.repository.ProjectDao;
import io.yak.ops.dao.repository.TaskInstanceDao;
import io.yak.ops.dao.repository.WorkflowDefinitionDao;
import io.yak.ops.dao.repository.WorkflowInstanceDao;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DataAnalysisServiceTest {

    private static final Logger serviceLogger = LoggerFactory.getLogger(DataAnalysisServiceImpl.class);

    @InjectMocks
    private DataAnalysisServiceImpl dataAnalysisServiceImpl;

    @Mock
    ProjectDao projectDao;

    @Mock
    ProjectService projectService;

    @Mock
    WorkflowInstanceDao workflowInstanceDao;

    @Mock
    WorkflowDefinitionDao workflowDefinitionDao;

    @Mock
    CommandMapper commandMapper;

    @Mock
    ErrorCommandMapper errorCommandMapper;

    @Mock
    TaskInstanceDao taskInstanceDao;

    @Mock
    private ResourcePermissionCheckService resourcePermissionCheckService;

    private Map<String, Object> resultMap;

    private User user;

    private Project project;

    @BeforeEach
    public void setUp() {

        user = new User();
        user.setUserType(UserType.ADMIN_USER);
        user.setId(1);
        project = new Project();
        project.setId(1);
        project.setCode(1);
        project.setName("test");
        resultMap = new HashMap<>();

        when(projectDao.queryByCode(1L)).thenReturn(project);
    }

    @AfterEach
    public void after() {
        user = null;
        projectDao = null;
        resultMap = null;
    }

    @Test
    public void testCountTaskStateByProject_success() {
        String startDate = "2020-02-11 16:02:18";
        String endDate = "2020-02-11 16:03:18";

        doNothing().when(projectService).checkProjectAndAuthThrowException(any(), anyLong(), any());

        // SUCCESS
        assertDoesNotThrow(
                () -> dataAnalysisServiceImpl.getTaskInstanceStateCountByProject(user, 1L, startDate, endDate));
    }

    @Test
    public void testCountTaskStateByProject_projectNotFound() {
        String startDate = "2020-02-11 16:02:18";
        String endDate = "2020-02-11 16:03:18";

        // checkProject false
        doThrow(new ServiceException(Status.PROJECT_NOT_FOUND, 1)).when(projectService)
                .checkProjectAndAuthThrowException(any(), anyLong(), any());
        assertThrowsServiceException(Status.PROJECT_NOT_FOUND,
                () -> dataAnalysisServiceImpl.getTaskInstanceStateCountByProject(user, 1L, startDate, endDate));
    }

    @Test
    public void testCountTaskStateByProject_paramValid() {
        doNothing().when(projectService).checkProjectAndAuthThrowException(any(), anyLong(), any());

        // when date in illegal format then return error message
        String startDate2 = "illegalDateString";
        String endDate2 = "illegalDateString";
        assertThrows(IllegalArgumentException.class,
                () -> dataAnalysisServiceImpl.getTaskInstanceStateCountByProject(user, 1L, startDate2, endDate2));

        // when one of date in illegal format then return error message
        String startDate3 = "2020-08-28 14:13:40";
        String endDate3 = "illegalDateString";
        assertThrows(IllegalArgumentException.class,
                () -> dataAnalysisServiceImpl.getTaskInstanceStateCountByProject(user, 1L, startDate3, endDate3));

        // when one of date in illegal format then return error message
        String startDate4 = "illegalDateString";
        String endDate4 = "2020-08-28 14:13:40";
        assertThrows(IllegalArgumentException.class,
                () -> dataAnalysisServiceImpl.getTaskInstanceStateCountByProject(user, 1L, startDate4, endDate4));
    }

    @Test
    public void testCountTaskStateByProject_allCountZero() {
        doNothing().when(projectService).checkProjectAndAuthThrowException(any(), anyLong(), any());

        // when general user doesn't have any task then return all count are 0
        user.setUserType(UserType.GENERAL_USER);
        when(resourcePermissionCheckService.userOwnedResourceIdsAcquisition(AuthorizationType.PROJECTS, 1,
                serviceLogger)).thenReturn(projectIds());
        when(taskInstanceDao.countTaskInstanceStateByProjectCodes(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        assertDoesNotThrow(() -> dataAnalysisServiceImpl.getTaskInstanceStateCountByProject(user, 1L, null, null));

    }

    @Test
    public void testCountTaskStateByProject_noData() {
        doNothing().when(projectService).checkProjectAndAuthThrowException(any(), anyLong(), any());
        when(resourcePermissionCheckService.userOwnedResourceIdsAcquisition(AuthorizationType.PROJECTS, 1,
                serviceLogger)).thenReturn(projectIds());

        // when instanceStateCounter return null, then return nothing
        user.setUserType(UserType.GENERAL_USER);
        when(taskInstanceDao.countTaskInstanceStateByProjectCodes(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        TaskInstanceCountVO taskInstanceStateCountByProject =
                dataAnalysisServiceImpl.getTaskInstanceStateCountByProject(user, 1L, null, null);
        assertThat(taskInstanceStateCountByProject).isNotNull();
    }

    @Test
    public void testCountProcessInstanceStateByProject() {
        String startDate = "2020-02-11 16:02:18";
        String endDate = "2020-02-11 16:03:18";

        // checkProject false
        doThrow(new ServiceException(Status.PROJECT_NOT_FOUND, 1)).when(projectService)
                .checkProjectAndAuthThrowException(any(), anyLong(), any());
        assertThrowsServiceException(Status.PROJECT_NOT_FOUND,
                () -> dataAnalysisServiceImpl.getWorkflowInstanceStateCountByProject(user, 1L, startDate, endDate));

        doNothing().when(projectService).checkProjectAndAuthThrowException(any(), anyLong(), any());

        // SUCCESS
        assertDoesNotThrow(
                () -> dataAnalysisServiceImpl.getWorkflowInstanceStateCountByProject(user, 1L, startDate, endDate));
    }

    @Test
    public void testCountDefinitionByUser() {
        doNothing().when(projectService).checkProjectAndAuthThrowException(any(), anyLong(), any());

        when(resourcePermissionCheckService.userOwnedResourceIdsAcquisition(AuthorizationType.PROJECTS, 1,
                serviceLogger)).thenReturn(projectIds());
        assertDoesNotThrow(() -> dataAnalysisServiceImpl.getWorkflowDefinitionCountByProject(user, 0L));

        when(resourcePermissionCheckService.userOwnedResourceIdsAcquisition(AuthorizationType.PROJECTS, 1,
                serviceLogger)).thenReturn(Collections.emptySet());
        assertDoesNotThrow(() -> dataAnalysisServiceImpl.getWorkflowDefinitionCountByProject(user, 0L));
    }

    @Test
    public void testCountCommandState() {
        User user = new User();
        user.setUserType(UserType.ADMIN_USER);
        user.setId(1);

        assertDoesNotThrow(() -> dataAnalysisServiceImpl.countCommandState(user));

        // when no command found then return all count are 0
        when(commandMapper.countCommandState(any(), any(), any())).thenReturn(Collections.emptyList());
        when(errorCommandMapper.countCommandState(any(), any(), any())).thenReturn(Collections.emptyList());
        when(resourcePermissionCheckService.userOwnedResourceIdsAcquisition(AuthorizationType.PROJECTS, 1,
                serviceLogger)).thenReturn(projectIds());

        List<CommandStateCount> commandStateCounts = dataAnalysisServiceImpl.countCommandState(user);
        assertThat(commandStateCounts).asList().extracting("errorCount").allMatch(count -> count.equals(0));
        assertThat(commandStateCounts).asList().extracting("normalCount").allMatch(count -> count.equals(0));

        // when command found then return combination result
        CommandCount normalCommandCount = new CommandCount();
        normalCommandCount.setCommandType(CommandType.START_PROCESS);
        normalCommandCount.setCount(10);
        CommandCount errorCommandCount = new CommandCount();
        errorCommandCount.setCommandType(CommandType.START_PROCESS);
        errorCommandCount.setCount(5);
        when(commandMapper.countCommandState(any(), any(), any()))
                .thenReturn(Collections.singletonList(normalCommandCount));
        when(errorCommandMapper.countCommandState(any(), any(), any()))
                .thenReturn(Collections.singletonList(errorCommandCount));

        commandStateCounts = dataAnalysisServiceImpl.countCommandState(user);

        CommandStateCount commandStateCount = new CommandStateCount();
        commandStateCount.setCommandState(CommandType.START_PROCESS);
        commandStateCount.setNormalCount(10);
        commandStateCount.setErrorCount(5);
        assertThat(commandStateCounts).asList().containsOnlyOnce(commandStateCount);
    }

    @Test
    public void testCountQueueState() {
        // when project check success when return all count are 0
        Map<String, Integer> stringIntegerMap = dataAnalysisServiceImpl.countQueueState(user);
        assertThat(stringIntegerMap).extracting("taskQueue", "taskKill")
                .isNotEmpty()
                .allMatch(count -> count.equals(0));
    }

    @Test
    public void testListPendingCommands() {
        IPage<Command> page = new Page<>(1, 10);
        page.setTotal(2L);
        page.setRecords(getList());
        when(commandMapper.queryCommandPage(any())).thenReturn(page);
        PageInfo<Command> list1 = dataAnalysisServiceImpl.listPendingCommands(user, 1L, 1, 10);
        assertThat(list1.getTotal()).isEqualTo(2);
        user.setUserType(UserType.GENERAL_USER);
        when(resourcePermissionCheckService.userOwnedResourceIdsAcquisition(AuthorizationType.PROJECTS, 1,
                serviceLogger))
                .thenReturn(projectIds());
        PageInfo<Command> list2 = dataAnalysisServiceImpl.listPendingCommands(user, 1L, 1, 10);
        assertThat(list2.getTotal()).isEqualTo(0);
        when(projectDao.queryByIds(any())).thenReturn(Collections.singletonList(project));
        when(workflowDefinitionDao.queryDefinitionCodeListByProjectCodes(any()))
                .thenReturn(Collections.singletonList(1L));
        when(commandMapper.queryCommandPageByIds(any(), any())).thenReturn(page);
        PageInfo<Command> list3 = dataAnalysisServiceImpl.listPendingCommands(user, 1L, 1, 10);
        assertThat(list3.getTotal()).isEqualTo(2);
    }

    @Test
    public void testListErrorCommand() {
        IPage<ErrorCommand> page = new Page<>(1, 10);
        page.setTotal(2L);
        page.setRecords(getErrorList());
        when(errorCommandMapper.queryErrorCommandPage(any())).thenReturn(page);
        PageInfo<ErrorCommand> list1 = dataAnalysisServiceImpl.listErrorCommand(user, 1L, 1, 10);
        assertThat(list1.getTotal()).isEqualTo(2);
        user.setUserType(UserType.GENERAL_USER);
        when(resourcePermissionCheckService.userOwnedResourceIdsAcquisition(AuthorizationType.PROJECTS, 1,
                serviceLogger))
                .thenReturn(projectIds());
        PageInfo<ErrorCommand> list2 = dataAnalysisServiceImpl.listErrorCommand(user, 1L, 1, 10);
        assertThat(list2.getTotal()).isEqualTo(0);
        when(projectDao.queryByIds(any())).thenReturn(Collections.singletonList(project));
        when(workflowDefinitionDao.queryDefinitionCodeListByProjectCodes(any()))
                .thenReturn(Collections.singletonList(1L));
        when(errorCommandMapper.queryErrorCommandPageByIds(any(), any())).thenReturn(page);
        PageInfo<ErrorCommand> list3 = dataAnalysisServiceImpl.listErrorCommand(user, 1L, 1, 10);
        assertThat(list3.getTotal()).isEqualTo(2);
    }

    private List<Command> getList() {
        List<Command> commandList = new ArrayList<>();
        Command command = new Command();
        command.setId(1);
        commandList.add(command);
        return commandList;
    }

    private List<ErrorCommand> getErrorList() {
        List<ErrorCommand> commandList = new ArrayList<>();
        ErrorCommand command = new ErrorCommand();
        command.setId(1);
        commandList.add(command);
        return commandList;
    }

    private Set<Integer> projectIds() {
        Set<Integer> projectIds = new HashSet<>();
        projectIds.add(1);
        return projectIds;
    }

    /**
     * get list
     */
    private List<ExecuteStatusCount> getTaskInstanceStateCounts() {
        List<ExecuteStatusCount> taskInstanceStateCounts = new ArrayList<>(1);
        ExecuteStatusCount executeStatusCount = new ExecuteStatusCount();
        executeStatusCount.setState(TaskExecutionStatus.RUNNING_EXECUTION);
        taskInstanceStateCounts.add(executeStatusCount);

        return taskInstanceStateCounts;
    }

    /**
     * get mock Project
     *
     * @param projectName projectName
     * @return Project
     */
    private Project getProject(String projectName) {
        Project project = new Project();
        project.setCode(1L);
        project.setId(1);
        project.setName(projectName);
        project.setUserId(1);
        return project;
    }

    private void putMsg(Map<String, Object> result, Status status, Object... statusParams) {
        result.put(Constants.STATUS, status);
        if (statusParams != null && statusParams.length > 0) {
            result.put(Constants.MSG, MessageFormat.format(status.getMsg(), statusParams));
        } else {
            result.put(Constants.MSG, status.getMsg());
        }
    }
}
