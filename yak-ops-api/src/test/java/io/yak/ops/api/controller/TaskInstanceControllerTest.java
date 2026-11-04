package io.yak.ops.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.service.TaskInstanceService;
import io.yak.ops.api.utils.PageInfo;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.enums.TaskExecuteType;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.entity.TaskInstance;
import io.yak.ops.dao.entity.User;
import io.yak.ops.plugin.task.api.enums.TaskExecutionStatus;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public class TaskInstanceControllerTest extends AbstractControllerTest {

    @InjectMocks
    private TaskInstanceController taskInstanceController;

    @Mock
    private TaskInstanceService taskInstanceService;

    @Test
    public void testQueryTaskListPaging() {

        Result result = new Result();
        Integer pageNo = 1;
        Integer pageSize = 20;
        PageInfo pageInfo = new PageInfo<TaskInstance>(pageNo, pageSize);
        result.setData(pageInfo);
        result.setCode(Status.SUCCESS.getCode());
        result.setMsg(Status.SUCCESS.getMsg());

        when(taskInstanceService.queryTaskListPaging(any(), eq(1L), eq(1), eq(""), eq(""), eq(""), any(), eq(""), any(),
                any(),
                eq(""), Mockito.any(), eq("192.168.xx.xx"), eq(TaskExecuteType.BATCH), any(), any()))
                .thenReturn(result);
        Result taskResult = taskInstanceController.queryTaskListPaging(null, 1L, 1, "", "", "",
                "", 1L, "", TaskExecutionStatus.SUCCESS, "192.168.xx.xx", "2020-01-01 00:00:00", "2020-01-02 00:00:00",
                TaskExecuteType.BATCH, pageNo, pageSize);
        Assertions.assertEquals(Integer.valueOf(Status.SUCCESS.getCode()), taskResult.getCode());
    }

    @Disabled
    @Test
    public void testForceTaskSuccess() throws Exception {
        MultiValueMap<String, String> paramsMap = new LinkedMultiValueMap<>();
        paramsMap.add("taskInstanceId", "104");

        Mockito.doNothing().when(taskInstanceService).forceTaskSuccess(any(User.class), anyLong(), anyInt());

        MvcResult mvcResult = mockMvc.perform(post("/projects/{projectName}/task-instance/force-success", "cxc_1113")
                .header(SESSION_ID, sessionId)
                .params(paramsMap))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        Result result = JSONUtils.parseObject(mvcResult.getResponse().getContentAsString(), Result.class);
        Assertions.assertEquals(Status.SUCCESS.getCode(), result.getCode().intValue());
    }

}
