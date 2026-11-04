package io.yak.ops.plugin.task.grpc;

import io.yak.ops.common.utils.JSONUtils;

import org.apache.commons.text.StringEscapeUtils;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test GrpcTask
 */
public class GrpcParametersTest {

    @Test
    void testGenerator() throws IOException {
        String paramData = "{" +
                "\"localParams\":[]," +
                "\"url\":\"127.0.0.1:50010\"," +
                "\"grpcServiceDefinition\":\"\"," +
                "\"grpcServiceDefinitionJSON\":\"" + toJSONEscaped(GrpcTaskTest.readResourceTextFile("taskTester.json"))
                + "\"," +
                "\"methodName\":\"TaskTester/TestOK\"," +
                "\"message\":\"{ \\\"username\\\":\\\"test username\\\" }\"," +
                "\"grpcCheckCondition\":\"STATUS_CODE_DEFAULT\"," +
                "\"condition\":\"\"," +
                "\"grpcConnectTimeoutMs\":\"10000\"" +
                "}";
        GrpcParameters grpcParameters = JSONUtils.parseObject(paramData, GrpcParameters.class);
        Assertions.assertEquals(10000, grpcParameters.getConnectTimeoutMs());
        Assertions.assertEquals("127.0.0.1:50010", grpcParameters.getUrl());
        Assertions.assertEquals("TaskTester/TestOK", grpcParameters.getMethodName());
        Assertions.assertEquals("{ \"username\":\"test username\" }", grpcParameters.getMessage());
        Assertions.assertEquals(GrpcCheckCondition.STATUS_CODE_DEFAULT, grpcParameters.getGrpcCheckCondition());
        Assertions.assertEquals("", grpcParameters.getCondition());
    }

    @Test
    void testCheckParameters() throws IOException {
        String paramData = "{" +
                "\"localParams\":[]," +
                "\"url\":\"127.0.0.1:50010\"," +
                "\"grpcServiceDefinition\":\"\"," +
                "\"grpcServiceDefinitionJSON\":\"" + toJSONEscaped(GrpcTaskTest.readResourceTextFile("taskTester.json"))
                + "\"," +
                "\"methodName\":\"TaskTester/TestOK\"," +
                "\"message\":\"{ \\\"username\\\":\\\"test username\\\" }\"," +
                "\"grpcCheckCondition\":\"STATUS_CODE_DEFAULT\"," +
                "\"condition\":\"\"," +
                "\"grpcConnectTimeoutMs\":\"10000\"" +
                "}";
        GrpcParameters grpcParameters = JSONUtils.parseObject(paramData, GrpcParameters.class);
        Assertions.assertTrue(grpcParameters.checkParameters());
        Assertions.assertEquals(10000, grpcParameters.getConnectTimeoutMs());
        Assertions.assertEquals("127.0.0.1:50010", grpcParameters.getUrl());
        Assertions.assertEquals("TaskTester/TestOK", grpcParameters.getMethodName());
        Assertions.assertEquals("{ \"username\":\"test username\" }", grpcParameters.getMessage());
        Assertions.assertEquals(GrpcCheckCondition.STATUS_CODE_DEFAULT, grpcParameters.getGrpcCheckCondition());
        Assertions.assertEquals("", grpcParameters.getCondition());
    }

    @Test
    void testCheckValues() throws IOException {
        String paramData = "{" +
                "\"localParams\":[]," +
                "\"url\":\"127.0.0.1:50010\"," +
                "\"grpcServiceDefinition\":\"\"," +
                "\"grpcServiceDefinitionJSON\":\"" + toJSONEscaped(GrpcTaskTest.readResourceTextFile("taskTester.json"))
                + "\"," +
                "\"methodName\":\"TaskTester/TestOK\"," +
                "\"message\":\"{ \\\"username\\\":\\\"test username\\\" }\"," +
                "\"grpcCheckCondition\":\"STATUS_CODE_DEFAULT\"," +
                "\"condition\":\"\"," +
                "\"grpcConnectTimeoutMs\":\"10000\"" +
                "}";
        GrpcParameters grpcParameters = JSONUtils.parseObject(paramData, GrpcParameters.class);
        Assertions.assertEquals(10000, grpcParameters.getConnectTimeoutMs());
        Assertions.assertEquals("127.0.0.1:50010", grpcParameters.getUrl());
        Assertions.assertEquals("TaskTester/TestOK", grpcParameters.getMethodName());
        Assertions.assertEquals("{ \"username\":\"test username\" }", grpcParameters.getMessage());
        Assertions.assertEquals(GrpcCheckCondition.STATUS_CODE_DEFAULT, grpcParameters.getGrpcCheckCondition());
        Assertions.assertEquals("", grpcParameters.getCondition());
        Assertions.assertEquals(0, grpcParameters.getResourceFilesList().size());
    }

    public static String toJSONEscaped(String input) {
        return StringEscapeUtils.escapeEcmaScript(input);
    }
}
