package io.yak.ops.plugin.alert.http;

import io.yak.ops.alert.api.AlertResult;
import io.yak.ops.common.model.OkHttpRequestHeaderContentType;
import io.yak.ops.common.utils.JSONUtils;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

@ExtendWith(MockitoExtension.class)
public class HttpSenderTest {

    private final List<MockWebServer> mockWebServers = new ArrayList<>();

    private Map<String, String> paramsMap = new HashMap<>();

    @AfterEach
    public void after() {
        mockWebServers.forEach(IOUtils::closeQuietly);
        mockWebServers.clear();
    }

    @Test
    void testHttpSenderGet() throws Exception {
        String msg = "msg_test";
        Map<String, String> headerParams = new HashMap<>();
        headerParams.put("msg", msg);
        paramsMap.put(HttpAlertConstants.NAME_HEADER_PARAMS, JSONUtils.toJsonString(headerParams));
        paramsMap.put(HttpAlertConstants.NAME_CONTENT_TYPE, OkHttpRequestHeaderContentType.APPLICATION_JSON.getValue());

        String mockGetUrl = createMockWebServer(String.format("/get/%s", msg), HttpStatus.SC_OK);
        String actualGetUrl = mockGetUrl.replace(msg, HttpAlertConstants.MSG_PARAMS);
        paramsMap.put(HttpAlertConstants.NAME_URL, actualGetUrl);

        paramsMap.put(HttpAlertConstants.NAME_REQUEST_TYPE, HttpRequestMethod.GET.name());

        HttpSender httpSender = new HttpSender(paramsMap);

        AlertResult alertResult = httpSender.send(msg);

        Assertions.assertTrue(alertResult.isSuccess());
        Assertions.assertTrue(alertResult.getMessage().contains(msg));
    }

    @Test
    void testHttpSenderPost() throws Exception {
        String msg = "msg_test";
        Map<String, String> bodyParams = new HashMap<>();
        bodyParams.put("msg", msg);
        paramsMap.put(HttpAlertConstants.NAME_BODY_PARAMS, JSONUtils.toJsonString(bodyParams));
        paramsMap.put(HttpAlertConstants.NAME_CONTENT_TYPE, OkHttpRequestHeaderContentType.APPLICATION_JSON.getValue());

        String mockPostUrl = createMockWebServer("/post", HttpStatus.SC_OK);
        paramsMap.put(HttpAlertConstants.NAME_URL, mockPostUrl);

        paramsMap.put(HttpAlertConstants.NAME_REQUEST_TYPE, HttpRequestMethod.POST.name());

        HttpSender httpSender = new HttpSender(paramsMap);

        AlertResult alertResult = httpSender.send(msg);

        Assertions.assertTrue(alertResult.isSuccess());
        Assertions.assertTrue(alertResult.getMessage().contains(msg));
    }

    @Test
    void testHttpSenderPut() throws Exception {
        String msg = "msg_test";
        Map<String, String> bodyParams = new HashMap<>();
        bodyParams.put("msg", msg);
        paramsMap.put(HttpAlertConstants.NAME_BODY_PARAMS, JSONUtils.toJsonString(bodyParams));
        paramsMap.put(HttpAlertConstants.NAME_CONTENT_TYPE, OkHttpRequestHeaderContentType.APPLICATION_JSON.getValue());

        String mockPostUrl = createMockWebServer("/post", HttpStatus.SC_OK);
        paramsMap.put(HttpAlertConstants.NAME_URL, mockPostUrl);

        paramsMap.put(HttpAlertConstants.NAME_REQUEST_TYPE, HttpRequestMethod.PUT.name());

        HttpSender httpSender = new HttpSender(paramsMap);

        AlertResult alertResult = httpSender.send(msg);

        Assertions.assertTrue(alertResult.isSuccess());
        Assertions.assertTrue(alertResult.getMessage().contains(msg));
    }

    private String createMockWebServer(String path, int actualResponseCode) throws IOException {
        MockWebServer server = new MockWebServer();
        mockWebServers.add(server);
        server.start();
        server.setDispatcher(generateMockDispatcher(actualResponseCode));
        return server.url(path).toString();
    }

    private Dispatcher generateMockDispatcher(int actualResponseCode) {
        return new Dispatcher() {

            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest request) {
                Map<String, String> responseMap = new HashMap<>();
                responseMap.put("url", request.getRequestUrl().toString());
                responseMap.put("headers", request.getHeaders().toString());
                responseMap.put("body", request.getBody().toString());
                String responseBody = JSONUtils.toJsonString(responseMap);
                return new MockResponse()
                        .setResponseCode(actualResponseCode)
                        .setBody(responseBody);
            }
        };
    }
}
