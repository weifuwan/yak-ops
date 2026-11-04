package io.yak.ops.api.controller;

import static io.yak.ops.api.AssertionsHelper.assertDoesNotThrow;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.service.impl.WorkflowLineageServiceImpl;
import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.enums.UserType;
import io.yak.ops.dao.entity.User;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * work flow lineage controller test
 */
@ExtendWith(MockitoExtension.class)
public class WorkflowTaskLineageControllerTest {

    @InjectMocks
    private WorkflowLineageController workflowLineageController;

    @Mock
    private WorkflowLineageServiceImpl workFlowLineageService;

    protected User user;

    @BeforeEach
    public void before() {
        User loginUser = new User();
        loginUser.setId(1);
        loginUser.setUserType(UserType.GENERAL_USER);
        loginUser.setUserName("admin");
        user = loginUser;
    }

    private void putMsg(Map<String, Object> result, Status status, Object... statusParams) {
        result.put(Constants.STATUS, status);
        if (statusParams != null && statusParams.length > 0) {
            result.put(Constants.MSG, MessageFormat.format(status.getMsg(), statusParams));
        } else {
            result.put(Constants.MSG, status.getMsg());
        }
    }

    @Test
    public void testQueryWorkFlowLineageByName() {
        long projectCode = 1L;
        String searchVal = "test";
        Mockito.when(workFlowLineageService.queryWorkFlowLineageByName(user, projectCode, searchVal))
                .thenReturn(Collections.emptyList());
        assertDoesNotThrow(() -> workflowLineageController.queryWorkFlowLineageByName(user, projectCode, searchVal));
    }

}
