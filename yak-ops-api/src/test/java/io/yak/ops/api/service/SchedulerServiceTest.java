package io.yak.ops.api.service;

import io.yak.ops.api.dto.ScheduleParam;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.exceptions.ServiceException;
import io.yak.ops.api.service.impl.SchedulerServiceImpl;
import io.yak.ops.api.validator.TenantExistValidator;
import io.yak.ops.common.enums.FailureStrategy;
import io.yak.ops.common.enums.Priority;
import io.yak.ops.common.enums.ReleaseState;
import io.yak.ops.common.enums.ScheduleMissedFirePolicy;
import io.yak.ops.common.enums.WarningType;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.Schedule;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.entity.WorkflowDefinition;
import io.yak.ops.dao.repository.ProjectDao;
import io.yak.ops.dao.repository.ScheduleDao;
import io.yak.ops.dao.repository.WorkflowDefinitionDao;
import io.yak.ops.scheduler.api.SchedulerApi;

import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SchedulerServiceTest extends BaseServiceTestTool {

    @InjectMocks
    private SchedulerServiceImpl schedulerService;

    @Mock
    private ScheduleDao scheduleDao;

    @Mock
    private ProjectDao projectDao;

    @Mock
    private WorkflowDefinitionDao workflowDefinitionDao;

    @Mock
    private ProjectService projectService;

    @Mock
    private ExecutorService executorService;

    @Mock
    private TenantExistValidator tenantExistValidator;

    @Mock
    private SchedulerApi schedulerApi;

    protected static User user;
    protected Exception exception;
    private static final String userName = "userName";
    private static final String projectName = "projectName";
    private static final long projectCode = 1L;
    private static final int userId = 1;
    private static final String processDefinitionName = "processDefinitionName";
    private static final long processDefinitionCode = 2L;
    private static final int processDefinitionVersion = 3;
    private static final int scheduleId = 3;
    private static final long environmentCode = 4L;

    @BeforeEach
    public void setUp() {
        user = new User();
        user.setUserName(userName);
        user.setId(userId);
    }

    @Test
    public void testScheduleParamMissedFirePolicyPresence() {
        String scheduleWithoutPolicy = "{\"startTime\":\"2019-12-16 00:00:00\","
                + "\"endTime\":\"2019-12-17 00:00:00\",\"crontab\":\"0 0 6 * * ? *\"}";
        String scheduleWithPolicy = "{\"startTime\":\"2019-12-16 00:00:00\","
                + "\"endTime\":\"2019-12-17 00:00:00\",\"crontab\":\"0 0 6 * * ? *\","
                + "\"missedFirePolicy\":\"SKIP_MISSED\"}";

        ScheduleParam withoutPolicy = JSONUtils.parseObject(scheduleWithoutPolicy, ScheduleParam.class);
        ScheduleParam withPolicy = JSONUtils.parseObject(scheduleWithPolicy, ScheduleParam.class);

        Assertions.assertEquals(ScheduleMissedFirePolicy.FIRE_ALL_MISSED, withoutPolicy.getMissedFirePolicy());
        Assertions.assertFalse(withoutPolicy.isMissedFirePolicySet());
        Assertions.assertEquals(ScheduleMissedFirePolicy.SKIP_MISSED, withPolicy.getMissedFirePolicy());
        Assertions.assertTrue(withPolicy.isMissedFirePolicySet());
    }

    @ParameterizedTest
    @EnumSource(ScheduleMissedFirePolicy.class)
    public void testInsertScheduleWithMissedFirePolicy(ScheduleMissedFirePolicy missedFirePolicy) {
        Project project = this.getProject();
        WorkflowDefinition workflowDefinition = this.getProcessDefinition();
        Schedule insertedSchedule = new Schedule();
        insertedSchedule.setId(scheduleId);
        Mockito.when(projectDao.queryByCode(projectCode)).thenReturn(project);
        Mockito.when(scheduleDao.queryByWorkflowDefinitionCode(processDefinitionCode)).thenReturn(null);
        Mockito.when(workflowDefinitionDao.queryByCode(processDefinitionCode))
                .thenReturn(Optional.of(workflowDefinition));
        Mockito.when(scheduleDao.queryById(Mockito.any())).thenReturn(insertedSchedule);

        Schedule result = schedulerService.insertSchedule(
                user, projectCode, processDefinitionCode, scheduleExpression(missedFirePolicy), WarningType.NONE, 0,
                FailureStrategy.CONTINUE, Priority.MEDIUM, "default", "tenantCode", environmentCode);

        ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
        Mockito.verify(scheduleDao).insert(scheduleCaptor.capture());
        Assertions.assertEquals(missedFirePolicy, scheduleCaptor.getValue().getMissedFirePolicy());
        Assertions.assertSame(insertedSchedule, result);
    }

    @Test
    public void testInsertScheduleDefaultsMissedFirePolicy() {
        Mockito.when(projectDao.queryByCode(projectCode)).thenReturn(this.getProject());
        Mockito.when(scheduleDao.queryByWorkflowDefinitionCode(processDefinitionCode)).thenReturn(null);
        Mockito.when(workflowDefinitionDao.queryByCode(processDefinitionCode))
                .thenReturn(Optional.of(this.getProcessDefinition()));
        Mockito.when(scheduleDao.queryById(Mockito.anyInt())).thenReturn(new Schedule());

        schedulerService.insertSchedule(
                user, projectCode, processDefinitionCode, scheduleExpression(null), WarningType.NONE, 0,
                FailureStrategy.CONTINUE, Priority.MEDIUM, "default", "tenantCode", environmentCode);

        ArgumentCaptor<Schedule> scheduleCaptor = ArgumentCaptor.forClass(Schedule.class);
        Mockito.verify(scheduleDao).insert(scheduleCaptor.capture());
        Assertions.assertEquals(ScheduleMissedFirePolicy.FIRE_ALL_MISSED,
                scheduleCaptor.getValue().getMissedFirePolicy());
    }

    @Test
    public void testInsertScheduleRejectsExplicitNullMissedFirePolicy() {
        assertInsertScheduleRejectsInvalidMissedFirePolicy("null");
    }

    @Test
    public void testInsertScheduleRejectsUnknownMissedFirePolicy() {
        assertInsertScheduleRejectsInvalidMissedFirePolicy("\"FIRE_ONCE_NWO\"");
    }

    @ParameterizedTest
    @EnumSource(ScheduleMissedFirePolicy.class)
    public void testUpdateScheduleWithMissedFirePolicy(ScheduleMissedFirePolicy missedFirePolicy) {
        Schedule schedule = this.getSchedule();
        schedule.setReleaseState(ReleaseState.OFFLINE);
        WorkflowDefinition workflowDefinition = this.getProcessDefinition();
        Mockito.when(projectDao.queryByCode(projectCode)).thenReturn(this.getProject());
        Mockito.when(scheduleDao.queryById(scheduleId)).thenReturn(schedule);
        Mockito.when(workflowDefinitionDao.queryByCode(processDefinitionCode))
                .thenReturn(Optional.of(workflowDefinition));

        schedulerService.updateSchedule(
                user, projectCode, scheduleId, scheduleExpression(missedFirePolicy), WarningType.NONE, 0,
                FailureStrategy.CONTINUE, Priority.MEDIUM, "default", "tenantCode", environmentCode);

        Assertions.assertEquals(missedFirePolicy, schedule.getMissedFirePolicy());
    }

    @Test
    public void testUpdateSchedulePreservesMissedFirePolicyWhenOmitted() {
        Schedule schedule = this.getSchedule();
        schedule.setReleaseState(ReleaseState.OFFLINE);
        schedule.setMissedFirePolicy(ScheduleMissedFirePolicy.SKIP_MISSED);
        WorkflowDefinition workflowDefinition = this.getProcessDefinition();
        Mockito.when(projectDao.queryByCode(projectCode)).thenReturn(this.getProject());
        Mockito.when(scheduleDao.queryById(scheduleId)).thenReturn(schedule);
        Mockito.when(workflowDefinitionDao.queryByCode(processDefinitionCode))
                .thenReturn(Optional.of(workflowDefinition));

        schedulerService.updateSchedule(
                user, projectCode, scheduleId, scheduleExpression(null), WarningType.NONE, 0,
                FailureStrategy.CONTINUE, Priority.MEDIUM, "default", "tenantCode", environmentCode);

        Assertions.assertEquals(ScheduleMissedFirePolicy.SKIP_MISSED, schedule.getMissedFirePolicy());
    }

    @Test
    public void testUpdateScheduleRejectsExplicitNullMissedFirePolicy() {
        assertUpdateScheduleRejectsInvalidMissedFirePolicy("null");
    }

    @Test
    public void testUpdateScheduleRejectsUnknownMissedFirePolicy() {
        assertUpdateScheduleRejectsInvalidMissedFirePolicy("\"FIRE_ONCE_NWO\"");
    }

    private void assertInsertScheduleRejectsInvalidMissedFirePolicy(String missedFirePolicyValue) {
        Mockito.when(projectDao.queryByCode(projectCode)).thenReturn(this.getProject());
        Mockito.when(scheduleDao.queryByWorkflowDefinitionCode(processDefinitionCode)).thenReturn(null);
        Mockito.when(workflowDefinitionDao.queryByCode(processDefinitionCode))
                .thenReturn(Optional.of(this.getProcessDefinition()));

        exception = Assertions.assertThrows(ServiceException.class,
                () -> schedulerService.insertSchedule(
                        user, projectCode, processDefinitionCode,
                        scheduleExpressionWithPolicyValue(missedFirePolicyValue),
                        WarningType.NONE, 0, FailureStrategy.CONTINUE, Priority.MEDIUM, "default", "tenantCode",
                        environmentCode));

        Assertions.assertEquals(Status.REQUEST_PARAMS_NOT_VALID_ERROR.getCode(),
                ((ServiceException) exception).getCode());
        Mockito.verify(scheduleDao, Mockito.never()).insert(Mockito.any());
    }

    private void assertUpdateScheduleRejectsInvalidMissedFirePolicy(String missedFirePolicyValue) {
        Schedule schedule = this.getSchedule();
        schedule.setReleaseState(ReleaseState.OFFLINE);
        schedule.setMissedFirePolicy(ScheduleMissedFirePolicy.SKIP_MISSED);
        Mockito.when(projectDao.queryByCode(projectCode)).thenReturn(this.getProject());
        Mockito.when(scheduleDao.queryById(scheduleId)).thenReturn(schedule);
        Mockito.when(workflowDefinitionDao.queryByCode(processDefinitionCode))
                .thenReturn(Optional.of(this.getProcessDefinition()));

        exception = Assertions.assertThrows(ServiceException.class,
                () -> schedulerService.updateSchedule(
                        user, projectCode, scheduleId, scheduleExpressionWithPolicyValue(missedFirePolicyValue),
                        WarningType.NONE, 0, FailureStrategy.CONTINUE, Priority.MEDIUM, "default", "tenantCode",
                        environmentCode));

        Assertions.assertEquals(Status.REQUEST_PARAMS_NOT_VALID_ERROR.getCode(),
                ((ServiceException) exception).getCode());
        Assertions.assertEquals(ScheduleMissedFirePolicy.SKIP_MISSED, schedule.getMissedFirePolicy());
        Mockito.verify(scheduleDao, Mockito.never()).updateById(Mockito.any());
    }

    private String scheduleExpression(ScheduleMissedFirePolicy missedFirePolicy) {
        String policy = missedFirePolicy == null ? "" : ",\"missedFirePolicy\":\"" + missedFirePolicy.name() + "\"";
        return scheduleExpressionWithPolicy(policy);
    }

    private String scheduleExpressionWithPolicyValue(String missedFirePolicyValue) {
        return scheduleExpressionWithPolicy(",\"missedFirePolicy\":" + missedFirePolicyValue);
    }

    private String scheduleExpressionWithPolicy(String policy) {
        return "{\"startTime\":\"2019-12-16 00:00:00\",\"endTime\":\"2019-12-17 00:00:00\","
                + "\"crontab\":\"0 0 6 * * ? *\",\"timezoneId\":\"Asia/Shanghai\"" + policy + "}";
    }

    @Test
    public void testDeleteSchedules() {
        Schedule schedule = this.getSchedule();

        // error schedule not exists
        exception = Assertions.assertThrows(ServiceException.class,
                () -> schedulerService.deleteSchedulesById(user, scheduleId));
        Assertions.assertEquals(Status.SCHEDULE_NOT_EXISTS.getCode(), ((ServiceException) exception).getCode());

        // error schedule already online
        schedule.setReleaseState(ReleaseState.ONLINE);
        Mockito.when(scheduleDao.queryById(scheduleId)).thenReturn(schedule);
        exception = Assertions.assertThrows(ServiceException.class,
                () -> schedulerService.deleteSchedulesById(user, scheduleId));
        Assertions.assertEquals(Status.SCHEDULE_STATE_ONLINE.getCode(), ((ServiceException) exception).getCode());
        schedule.setReleaseState(ReleaseState.OFFLINE);

        // error user not own schedule
        int notOwnUserId = 2;
        schedule.setUserId(notOwnUserId);
        Mockito.when(scheduleDao.queryById(scheduleId)).thenReturn(schedule);
        exception = Assertions.assertThrows(ServiceException.class,
                () -> schedulerService.deleteSchedulesById(user, scheduleId));
        Assertions.assertEquals(Status.USER_NO_OPERATION_PERM.getMsg(), exception.getMessage());
        schedule.setUserId(userId);

        // error process definition not exists
        Mockito.when(scheduleDao.queryById(scheduleId)).thenReturn(schedule);
        exception = Assertions.assertThrows(ServiceException.class,
                () -> schedulerService.deleteSchedulesById(user, scheduleId));
        Assertions.assertEquals(Status.WORKFLOW_DEFINITION_NOT_EXIST.getCode(),
                ((ServiceException) exception).getCode());

        // error project permissions
        Mockito.when(workflowDefinitionDao.queryByCode(processDefinitionCode))
                .thenReturn(Optional.of(this.getProcessDefinition()));
        Project project = this.getProject();
        Mockito.when(projectDao.queryByCode(projectCode)).thenReturn(project);
        Mockito.doThrow(new ServiceException(Status.USER_NO_WRITE_PROJECT_PERM)).when(projectService)
                .checkHasProjectWritePermissionThrowException(user, project);
        exception = Assertions.assertThrows(ServiceException.class,
                () -> schedulerService.deleteSchedulesById(user, scheduleId));
        Assertions.assertEquals(Status.USER_NO_WRITE_PROJECT_PERM.getCode(),
                ((ServiceException) exception).getCode());

        // error delete mapper
        Mockito.doNothing().when(projectService).checkHasProjectWritePermissionThrowException(user, project);
        Mockito.when(scheduleDao.deleteById(scheduleId)).thenReturn(false);
        exception = Assertions.assertThrows(ServiceException.class,
                () -> schedulerService.deleteSchedulesById(user, scheduleId));
        Assertions.assertEquals(Status.DELETE_SCHEDULE_BY_ID_ERROR.getCode(), ((ServiceException) exception).getCode());

        // success
        Mockito.when(scheduleDao.deleteById(scheduleId)).thenReturn(true);
        Assertions.assertDoesNotThrow(() -> schedulerService.deleteSchedulesById(user, scheduleId));
    }

    @Test
    public void testReadOnlyUserCannotChangeSchedule() {
        Project project = this.getProject();
        Mockito.when(projectDao.queryByCode(projectCode)).thenReturn(project);
        Mockito.doThrow(new ServiceException(Status.USER_NO_WRITE_PROJECT_PERM)).when(projectService)
                .checkHasProjectWritePermissionThrowException(user, project);
        Mockito.doThrow(new ServiceException(Status.USER_NO_WRITE_PROJECT_PERM)).when(projectService)
                .checkHasProjectWritePermissionThrowException(user, projectCode);

        Assertions.assertThrows(ServiceException.class,
                () -> schedulerService.insertSchedule(
                        user, projectCode, processDefinitionCode, scheduleExpression(null), WarningType.NONE, 0,
                        FailureStrategy.CONTINUE, Priority.MEDIUM, "default", "tenantCode", environmentCode));
        Assertions.assertThrows(ServiceException.class,
                () -> schedulerService.updateSchedule(
                        user, projectCode, scheduleId, scheduleExpression(null), WarningType.NONE, 0,
                        FailureStrategy.CONTINUE, Priority.MEDIUM, "default", "tenantCode", environmentCode));
        Assertions.assertThrows(ServiceException.class,
                () -> schedulerService.updateScheduleByWorkflowDefinitionCode(
                        user, projectCode, processDefinitionCode, scheduleExpression(null), WarningType.NONE, 0,
                        FailureStrategy.CONTINUE, Priority.MEDIUM, "default", "tenantCode", environmentCode));
        Assertions.assertThrows(ServiceException.class,
                () -> schedulerService.onlineScheduler(user, projectCode, scheduleId));
        Assertions.assertThrows(ServiceException.class,
                () -> schedulerService.offlineScheduler(user, projectCode, scheduleId));

        Mockito.verify(scheduleDao, Mockito.never()).insert(Mockito.any());
        Mockito.verify(scheduleDao, Mockito.never()).updateById(Mockito.any());
    }

    @Test
    public void testProjectCodeCannotAuthorizeScheduleFromAnotherProject() {
        Schedule schedule = this.getSchedule();
        WorkflowDefinition workflowDefinition = this.getProcessDefinition();
        workflowDefinition.setProjectCode(2L);
        Mockito.when(scheduleDao.queryById(scheduleId)).thenReturn(schedule);
        Mockito.when(workflowDefinitionDao.queryByCode(processDefinitionCode))
                .thenReturn(Optional.of(workflowDefinition));

        ServiceException onlineEx = Assertions.assertThrows(ServiceException.class,
                () -> schedulerService.onlineScheduler(user, projectCode, scheduleId));
        ServiceException offlineEx = Assertions.assertThrows(ServiceException.class,
                () -> schedulerService.offlineScheduler(user, projectCode, scheduleId));

        Assertions.assertEquals(Status.SCHEDULE_NOT_EXISTS.getCode(), onlineEx.getCode());
        Assertions.assertEquals(Status.SCHEDULE_NOT_EXISTS.getCode(), offlineEx.getCode());
        Mockito.verify(scheduleDao, Mockito.never()).updateById(Mockito.any());
        Mockito.verifyNoInteractions(schedulerApi);
    }

    private Project getProject() {
        Project project = new Project();
        project.setId(1);
        project.setName(projectName);
        project.setCode(projectCode);
        project.setUserId(userId);
        return project;
    }

    private WorkflowDefinition getProcessDefinition() {
        WorkflowDefinition workflowDefinition = new WorkflowDefinition();
        workflowDefinition.setName(processDefinitionName);
        workflowDefinition.setCode(processDefinitionCode);
        workflowDefinition.setProjectCode(projectCode);
        workflowDefinition.setVersion(processDefinitionVersion);
        workflowDefinition.setUserId(userId);
        return workflowDefinition;
    }

    private Schedule getSchedule() {
        Schedule schedule = new Schedule();
        schedule.setId(scheduleId);
        schedule.setWorkflowDefinitionCode(processDefinitionCode);
        schedule.setEnvironmentCode(environmentCode);
        schedule.setUserId(userId);
        return schedule;
    }

}
