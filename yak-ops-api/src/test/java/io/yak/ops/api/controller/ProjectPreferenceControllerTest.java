package io.yak.ops.api.controller;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.service.impl.ProjectPreferenceServiceImpl;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.User;

import org.junit.jupiter.api.Assertions;
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
public class ProjectPreferenceControllerTest {

    @InjectMocks
    private ProjectPreferenceController projectPreferenceController;

    @Mock
    private ProjectPreferenceServiceImpl projectPreferenceService;

    @Test
    public void testUpdateProjectPreference() {
        User loginUser = getGeneralUser();

        Mockito.when(projectPreferenceService.updateProjectPreference(Mockito.any(), Mockito.anyLong(),
                Mockito.anyString())).thenReturn(getSuccessResult());

        Result result = projectPreferenceController.updateProjectPreference(loginUser, 1, "value");
        Assertions.assertEquals(Status.SUCCESS.getCode(), result.getCode());
    }

    @Test
    public void testQueryProjectPreferenceByProjectCode() {
        User loginUser = getGeneralUser();

        Mockito.when(projectPreferenceService.queryProjectPreferenceByProjectCode(Mockito.any(), Mockito.anyLong()))
                .thenReturn(getSuccessResult());
        Result result = projectPreferenceController.queryProjectPreferenceByProjectCode(loginUser, 1);
        Assertions.assertEquals(Status.SUCCESS.getCode(), result.getCode());
    }

    @Test
    public void testEnableProjectPreference() {
        User loginUser = getGeneralUser();

        Mockito.when(
                projectPreferenceService.enableProjectPreference(Mockito.any(), Mockito.anyLong(), Mockito.anyInt()))
                .thenReturn(getSuccessResult());
        Result result = projectPreferenceController.enableProjectPreference(loginUser, 1, 1);
        Assertions.assertEquals(Status.SUCCESS.getCode(), result.getCode());
    }

    private User getGeneralUser() {
        User loginUser = new User();
        loginUser.setUserType(UserType.GENERAL_USER);
        loginUser.setUserName("userName");
        loginUser.setId(1);
        return loginUser;
    }

    private Result getSuccessResult() {
        Result result = new Result();
        result.setCode(Status.SUCCESS.getCode());
        result.setMsg(Status.SUCCESS.getMsg());
        return result;
    }

}
