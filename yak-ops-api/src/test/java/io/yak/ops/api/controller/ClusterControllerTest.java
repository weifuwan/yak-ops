package io.yak.ops.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.yak.ops.api.enums.Status;
import io.yak.ops.api.utils.Result;
import io.yak.ops.common.utils.JSONUtils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Preconditions;

public class ClusterControllerTest extends AbstractControllerTest {

    public static final String clusterName = "Cluster1";
    public static final String config = "{\"k8s\":\"apiVersion: v1\"}";
    public static final String desc = "this is cluster description";
    private static final Logger logger = LoggerFactory.getLogger(ClusterControllerTest.class);
    private String clusterCode;

    @BeforeEach
    public void before() throws Exception {
        testCreateCluster();
    }

    @Override
    @AfterEach
    public void after() throws Exception {
        testDeleteCluster();
    }

    public void testCreateCluster() throws Exception {

        MultiValueMap<String, String> paramsMap = new LinkedMultiValueMap<>();
        paramsMap.add("name", clusterName);
        paramsMap.add("config", config);
        paramsMap.add("description", desc);

        MvcResult mvcResult = mockMvc.perform(post("/cluster/create")
                .header(SESSION_ID, sessionId)
                .params(paramsMap))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        Result result = JSONUtils.parseObject(mvcResult.getResponse().getContentAsString(),
                new TypeReference<Result<String>>() {
                });
        logger.info(result.toString());
        Assertions.assertTrue(result != null && result.isSuccess());
        Assertions.assertNotNull(result.getData());
        logger.info("create cluster return result:{}", mvcResult.getResponse().getContentAsString());

        clusterCode = (String) result.getData();
    }

    @Test
    public void testUpdateCluster() throws Exception {
        MultiValueMap<String, String> paramsMap = new LinkedMultiValueMap<>();
        paramsMap.add("code", clusterCode);
        paramsMap.add("name", "cluster_test_update");
        paramsMap.add("config", "{\"k8s\":\"apiVersion: v1\"}");
        paramsMap.add("desc", "the test cluster update");

        MvcResult mvcResult = mockMvc.perform(post("/cluster/update")
                .header(SESSION_ID, sessionId)
                .params(paramsMap))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        Result result = JSONUtils.parseObject(mvcResult.getResponse().getContentAsString(), Result.class);
        logger.info(result.toString());
        Assertions.assertTrue(result != null && result.isSuccess());
        logger.info("update cluster return result:{}", mvcResult.getResponse().getContentAsString());

    }

    @Test
    public void testQueryClusterByCodeIsNotExposed() throws Exception {
        MultiValueMap<String, String> paramsMap = new LinkedMultiValueMap<>();
        paramsMap.add("clusterCode", clusterCode);

        mockMvc.perform(get("/cluster/query-by-code")
                .header(SESSION_ID, sessionId)
                .params(paramsMap))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testQueryClusterListPaging() throws Exception {
        MultiValueMap<String, String> paramsMap = new LinkedMultiValueMap<>();
        paramsMap.add("searchVal", "test");
        paramsMap.add("pageSize", "2");
        paramsMap.add("pageNo", "2");

        MvcResult mvcResult = mockMvc.perform(get("/cluster/list-paging")
                .header(SESSION_ID, sessionId)
                .params(paramsMap))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        Result result = JSONUtils.parseObject(mvcResult.getResponse().getContentAsString(), Result.class);
        logger.info(result.toString());
        Assertions.assertTrue(result != null && result.isSuccess());
        logger.info("query list-paging cluster return result:{}", mvcResult.getResponse().getContentAsString());
    }

    @Test
    public void testQueryAllClusterList() throws Exception {
        MultiValueMap<String, String> paramsMap = new LinkedMultiValueMap<>();

        MvcResult mvcResult = mockMvc.perform(get("/cluster/query-cluster-list")
                .header(SESSION_ID, sessionId)
                .params(paramsMap))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        Result result = JSONUtils.parseObject(mvcResult.getResponse().getContentAsString(), Result.class);
        logger.info(result.toString());
        Assertions.assertTrue(result != null && result.isSuccess());
        logger.info("query all cluster return result:{}", mvcResult.getResponse().getContentAsString());

    }

    @Test
    public void testVerifyCluster() throws Exception {
        MultiValueMap<String, String> paramsMap = new LinkedMultiValueMap<>();
        paramsMap.add("clusterName", clusterName);

        MvcResult mvcResult = mockMvc.perform(post("/cluster/verify-cluster")
                .header(SESSION_ID, sessionId)
                .params(paramsMap))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        Result result = JSONUtils.parseObject(mvcResult.getResponse().getContentAsString(), Result.class);
        logger.info(result.toString());
        Assertions.assertTrue(result.isStatus(Status.CLUSTER_NAME_EXISTS));
        logger.info("verify cluster return result:{}", mvcResult.getResponse().getContentAsString());

    }

    private void testDeleteCluster() throws Exception {
        Preconditions.checkNotNull(clusterCode);

        MultiValueMap<String, String> paramsMap = new LinkedMultiValueMap<>();
        paramsMap.add("clusterCode", clusterCode);

        MvcResult mvcResult = mockMvc.perform(post("/cluster/delete")
                .header(SESSION_ID, sessionId)
                .params(paramsMap))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn();

        Result result = JSONUtils.parseObject(mvcResult.getResponse().getContentAsString(), Result.class);
        logger.info(result.toString());
        Assertions.assertTrue(result != null && result.isSuccess());
        logger.info("delete cluster return result:{}", mvcResult.getResponse().getContentAsString());
    }
}
