package io.yak.ops.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.yak.ops.api.utils.Result;
import io.yak.ops.common.utils.JSONUtils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * queue controller test
 */
public class TaskGroupQueueControllerTest extends AbstractControllerTest {

    private static Logger logger = LoggerFactory.getLogger(TaskGroupQueueControllerTest.class);

    private static final String QUEUE_CREATE_STRING = "queue1";

    @Test
    public void queryTasksByGroupId() throws Exception {
        MultiValueMap<String, String> paramsMap = new LinkedMultiValueMap<>();
        paramsMap.add("groupId", "1");
        paramsMap.add("pageNo", "1");
        paramsMap.add("pageSize", "10");
        MvcResult mvcResult = mockMvc.perform(get("/task-group/query-list-by-group-id")
                .header(SESSION_ID, sessionId)
                .params(paramsMap))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        Result result = JSONUtils.parseObject(mvcResult.getResponse().getContentAsString(), Result.class);
        Assertions.assertTrue(result != null && result.isSuccess());
        logger.info("query list queue return result:{}", mvcResult.getResponse().getContentAsString());
    }
}
