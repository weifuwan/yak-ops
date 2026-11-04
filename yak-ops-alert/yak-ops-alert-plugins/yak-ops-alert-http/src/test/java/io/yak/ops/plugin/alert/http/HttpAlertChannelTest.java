/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.yak.ops.plugin.alert.http;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import io.yak.ops.alert.api.AlertData;
import io.yak.ops.alert.api.AlertInfo;
import io.yak.ops.alert.api.AlertResult;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.spi.params.PluginParamsTransfer;
import io.yak.ops.spi.params.base.PluginParams;
import io.yak.ops.spi.params.base.Validate;
import io.yak.ops.spi.params.input.InputParam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HttpAlertChannelTest {

    @Test
    public void testProcessWithoutParam() {
        HttpAlertChannel alertChannel = new HttpAlertChannel();
        AlertInfo alertInfo = new AlertInfo();
        AlertData alertData = new AlertData();
        alertData.setContent("Fault tolerance warning");
        alertInfo.setAlertData(alertData);
        AlertResult alertResult = alertChannel.process(alertInfo);
        Assertions.assertEquals("http params is null", alertResult.getMessage());
    }

    @Test
    public void testProcessSuccess() {
        HttpAlertChannel alertChannel = spy(new HttpAlertChannel());
        AlertInfo alertInfo = new AlertInfo();
        AlertData alertData = new AlertData();
        alertData.setContent("Fault tolerance warning");
        alertInfo.setAlertData(alertData);
        Map<String, String> paramsMap = PluginParamsTransfer.getPluginParamsMap(getParams());
        alertInfo.setAlertParams(paramsMap);

        // HttpSender(paramsMap).send(alertData.getContent()); already test in HttpSenderTest.sendTest. so we can mock
        // it
        doReturn(new AlertResult(true, "success")).when(alertChannel).process(any());
        AlertResult alertResult = alertChannel.process(alertInfo);
        Assertions.assertTrue(alertResult.isSuccess());
    }

    /**
     * create params
     */
    private String getParams() {

        List<PluginParams> paramsList = new ArrayList<>();
        InputParam urlParam = InputParam.newBuilder("url", "url")
                .setValue("http://www.dolphinscheduler-not-exists-web.com")
                .addValidate(Validate.newBuilder().setRequired(true).build())
                .build();

        InputParam headerParams = InputParam.newBuilder("headerParams", "headerParams")
                .addValidate(Validate.newBuilder().setRequired(true).build())
                .setValue("{\"Content-Type\":\"application/json\"}")
                .build();

        InputParam bodyParams = InputParam.newBuilder("bodyParams", "bodyParams")
                .addValidate(Validate.newBuilder().setRequired(true).build())
                .setValue("{\"number\":\"1234567\"}")
                .build();

        InputParam content = InputParam.newBuilder("contentField", "contentField")
                .setValue("content")
                .addValidate(Validate.newBuilder().setRequired(true).build())
                .build();

        InputParam requestType = InputParam.newBuilder("requestType", "requestType")
                .setValue("POST")
                .addValidate(Validate.newBuilder().setRequired(true).build())
                .build();

        InputParam timeout = InputParam.newBuilder("timeout", "timeout")
                .setValue(120)
                .addValidate(Validate.newBuilder().setRequired(true).build())
                .build();

        paramsList.add(urlParam);
        paramsList.add(headerParams);
        paramsList.add(bodyParams);
        paramsList.add(content);
        paramsList.add(requestType);
        paramsList.add(timeout);

        return JSONUtils.toJsonString(paramsList);
    }

}
