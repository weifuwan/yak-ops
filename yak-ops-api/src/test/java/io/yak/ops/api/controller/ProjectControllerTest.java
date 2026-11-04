package io.yak.ops.api.controller;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.service.impl.ProjectServiceImpl;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.Project;
import io.yak.ops.dao.entity.User;
import io.yak.ops.dao.repository.ProjectDao;

import java.text.MessageFormat;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ProjectControllerTest {

    protected User user;

    @InjectMocks
    private ProjectController projectController;

    @Mock
    private ProjectServiceImpl projectService;

    @Mock
    private ProjectDao projectDao;

    @BeforeEach
    public void before() {
        User loginUser = new User();
        loginUser.setId(1);
        loginUser.setUserType(UserType.GENERAL_USER);
        loginUser.setUserName("admin");
        user = loginUser;
    }

    @Test
    public void testUpdateProject() {
        Result result = new Result();
        putMsg(result, Status.SUCCESS);

        long projectCode = 1L;
        String projectName = "test";
        String desc = "";
        Mockito.when(projectService.update(user, projectCode, projectName, desc)).thenReturn(result);
        Result response = projectController.updateProject(user, projectCode, projectName, desc);
        Assertions.assertEquals(Status.SUCCESS.getCode(), response.getCode().intValue());
    }

    @Test
    public void testQueryProjectByCode() {
        Result result = new Result();
        putMsg(result, Status.SUCCESS);
        long projectCode = 1L;
        Mockito.when(projectDao.queryByCode(projectCode)).thenReturn(getProject());
        Mockito.when(projectService.queryByCode(user, projectCode)).thenReturn(result);
        Result response = projectController.queryProjectByCode(user, projectCode);
        Assertions.assertEquals(Status.SUCCESS.getCode(), response.getCode().intValue());
    }

    @Test
    public void testQueryProjectListPaging() {
        int pageNo = 1;
        int pageSize = 10;
        String searchVal = "";

        Result result = Result.success(new PageInfo<Project>(1, 10));

        Mockito.when(projectService.queryProjectListPaging(user, pageSize, pageNo, searchVal)).thenReturn(result);
        Result response = projectController.queryProjectListPaging(user, searchVal, pageSize, pageNo);

        Assertions.assertTrue(response != null && response.isSuccess());
    }

    @Test
    public void testQueryUnauthorizedProject() {
        Result result = new Result();
        putMsg(result, Status.SUCCESS);
        Mockito.when(projectService.queryUnauthorizedProject(user, 2)).thenReturn(result);
        Result response = projectController.queryUnauthorizedProject(user, 2);
        Assertions.assertEquals(Status.SUCCESS.getCode(), response.getCode().intValue());
    }

    @Test
    public void testQueryAuthorizedProject() {
        Result result = new Result();
        putMsg(result, Status.SUCCESS);
        Mockito.when(projectService.queryAuthorizedProject(user, 2)).thenReturn(result);
        Result response = projectController.queryAuthorizedProject(user, 2);
        Assertions.assertEquals(Status.SUCCESS.getCode(), response.getCode().intValue());
    }

    @Test
    public void testQueryAuthorizedUser() {
        Result result = new Result();
        this.putMsg(result, Status.SUCCESS);

        Mockito.when(this.projectService.queryAuthorizedUser(this.user, 3682329499136L)).thenReturn(result);
        Result response = this.projectController.queryAuthorizedUser(this.user, 3682329499136L);
        Assertions.assertEquals(Status.SUCCESS.getCode(), response.getCode().intValue());
    }

    @Test
    public void testQueryAllProjectList() {
        User user = new User();
        user.setId(0);
        Result result = new Result();
        putMsg(result, Status.SUCCESS);
        Mockito.when(projectService.queryAllProjectList(user)).thenReturn(result);
        Result response = projectController.queryAllProjectList(user);
        Assertions.assertEquals(Status.SUCCESS.getCode(), response.getCode().intValue());
    }
    @Test
    public void testQueryAllProjectListForDependent() {
        User user = new User();
        user.setId(0);
        Result result = new Result();
        putMsg(result, Status.SUCCESS);
        Mockito.when(projectService.queryAllProjectListForDependent()).thenReturn(result);
        Result response = projectController.queryAllProjectListForDependent(user);
        Assertions.assertEquals(Status.SUCCESS.getCode(), response.getCode().intValue());
    }
    private Project getProject() {
        Project project = new Project();
        project.setCode(1L);
        project.setId(1);
        project.setName("test");
        project.setUserId(1);
        return project;
    }

    private void putMsg(Result result, Status status, Object... statusParams) {
        result.setCode(status.getCode());
        if (statusParams != null && statusParams.length > 0) {
            result.setMsg(MessageFormat.format(status.getMsg(), statusParams));
        } else {
            result.setMsg(status.getMsg());
        }
    }
}
