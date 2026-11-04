package io.yak.ops.api.exceptions;

import io.yak.ops.api.controller.AccessTokenController;
import io.yak.ops.api.controller.WorkflowDefinitionController;
import io.yak.ops.api.enums.Status;
import io.yak.ops.api.utils.Result;
import io.yak.ops.dao.entity.User;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;

public class ApiExceptionHandlerTest {

    @Test
    public void exceptionHandler() throws NoSuchMethodException {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        AccessTokenController controller = new AccessTokenController();
        Method method =
                controller.getClass().getMethod("createToken", User.class, int.class, String.class, String.class);
        HandlerMethod hm = new HandlerMethod(controller, method);
        ResponseEntity<Result<Object>> response = handler.exceptionHandler(new RuntimeException("test exception"), hm);
        Assertions.assertEquals(Status.CREATE_ACCESS_TOKEN_ERROR.getCode(), response.getBody().getCode().intValue());
    }

    @Test
    public void exceptionHandlerRuntime() throws NoSuchMethodException {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        WorkflowDefinitionController controller = new WorkflowDefinitionController();
        Method method =
                controller.getClass().getMethod("queryAllWorkflowDefinitionByProjectCode", User.class, long.class);
        HandlerMethod hm = new HandlerMethod(controller, method);
        ResponseEntity<Result<Object>> response = handler.exceptionHandler(new RuntimeException("test exception"), hm);
        Assertions.assertEquals(Status.QUERY_WORKFLOW_DEFINITION_LIST.getCode(),
                response.getBody().getCode().intValue());
    }
}
