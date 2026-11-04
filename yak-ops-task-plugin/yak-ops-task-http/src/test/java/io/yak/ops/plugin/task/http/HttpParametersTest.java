package io.yak.ops.plugin.task.http;

import io.yak.ops.common.utils.JSONUtils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * http parameter
 */
public class HttpParametersTest {

    @Test
    public void testGenerator() {
        String paramData = "{\"localParams\":[],\"httpParams\":[],\"url\":\"https://www.baidu.com/\","
                + "\"httpMethod\":\"GET\",\"httpCheckCondition\":\"STATUS_CODE_DEFAULT\",\"condition\":\"\",\"connectTimeout\":\"10000\"}";
        HttpParameters httpParameters = JSONUtils.parseObject(paramData, HttpParameters.class);

        Assertions.assertEquals(10000, httpParameters.getConnectTimeout());
        Assertions.assertEquals("https://www.baidu.com/", httpParameters.getUrl());
        Assertions.assertEquals(HttpRequestMethod.GET, httpParameters.getHttpRequestMethod());
        Assertions.assertEquals(HttpCheckCondition.STATUS_CODE_DEFAULT, httpParameters.getHttpCheckCondition());
        Assertions.assertEquals("", httpParameters.getCondition());

    }

    @Test
    public void testCheckParameters() {
        String paramData = "{\"localParams\":[],\"httpParams\":[],\"url\":\"https://www.baidu.com/\","
                + "\"httpMethod\":\"GET\",\"httpCheckCondition\":\"STATUS_CODE_DEFAULT\",\"condition\":\"\",\"connectTimeout\":\"10000\"}";
        HttpParameters httpParameters = JSONUtils.parseObject(paramData, HttpParameters.class);

        Assertions.assertTrue(httpParameters.checkParameters());
        Assertions.assertEquals(10000, httpParameters.getConnectTimeout());
        Assertions.assertEquals("https://www.baidu.com/", httpParameters.getUrl());
        Assertions.assertEquals(HttpRequestMethod.GET, httpParameters.getHttpRequestMethod());
        Assertions.assertEquals(HttpCheckCondition.STATUS_CODE_DEFAULT, httpParameters.getHttpCheckCondition());
        Assertions.assertEquals("", httpParameters.getCondition());

    }

    @Test
    public void testCheckValues() {
        String paramData = "{\"localParams\":[],\"httpParams\":[],\"url\":\"https://www.baidu.com/\","
                + "\"httpMethod\":\"GET\",\"httpCheckCondition\":\"STATUS_CODE_DEFAULT\",\"condition\":\"\",\"connectTimeout\":\"10000\"}";
        HttpParameters httpParameters = JSONUtils.parseObject(paramData, HttpParameters.class);

        Assertions.assertTrue(httpParameters.checkParameters());
        Assertions.assertEquals(10000, httpParameters.getConnectTimeout());
        Assertions.assertEquals("https://www.baidu.com/", httpParameters.getUrl());
        Assertions.assertEquals(HttpRequestMethod.GET, httpParameters.getHttpRequestMethod());
        Assertions.assertEquals(HttpCheckCondition.STATUS_CODE_DEFAULT, httpParameters.getHttpCheckCondition());
        Assertions.assertEquals("", httpParameters.getCondition());
        Assertions.assertEquals(0, httpParameters.getResourceFilesList().size());
    }

}
