package io.yak.ops.server.master.integration.fake;

import static com.google.common.truth.Truth.assertThat;

import io.yak.ops.common.utils.JSONUtils;

import org.junit.jupiter.api.Test;

class LogicFakeTaskParametersTest {

    @Test
    public void testFakeParamsToJson() {
        final LogicFakeTaskParameters logicFakeTaskParameters = new LogicFakeTaskParameters();
        logicFakeTaskParameters.setShellScript("console.log('hello world');");
        String jsonString = JSONUtils.toJsonString(logicFakeTaskParameters);
        assertThat(jsonString)
                .isEqualTo("{\"localParams\":null,\"varPool\":[],\"shellScript\":\"console.log('hello world');\"}");
    }

    @Test
    public void testJsonToFakeParams() {
        final String json = "{\"localParams\":null,\"varPool\":[],\"shellScript\":\"console.log('hello world');\"}";
        final LogicFakeTaskParameters logicFakeTaskParameters =
                JSONUtils.parseObject(json, LogicFakeTaskParameters.class);
        assertThat(logicFakeTaskParameters).isNotNull();

        assertThat(logicFakeTaskParameters.getShellScript()).isEqualTo("console.log('hello world');");
    }

}
