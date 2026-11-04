package io.yak.ops.plugin.alert.wechat;

import io.yak.ops.alert.api.AlertConstants;
import io.yak.ops.alert.api.AlertResult;
import io.yak.ops.alert.api.ShowType;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WeChatSenderTest {

    private static Map<String, String> weChatConfig = new HashMap<>();

    private String content = "[{\"id\":\"69\","
            +
            "\"name\":\"UserBehavior-0--1193959466\","
            +
            "\"Job name\":\"Start workflow\","
            +
            "\"State\":\"SUCCESS\","
            +
            "\"Recovery\":\"NO\","
            +
            "\"Run time\":\"1\","
            +
            "\"Start time\": \"2018-08-06 10:31:34.0\","
            +
            "\"End time\": \"2018-08-06 10:31:49.0\","
            +
            "\"Host\": \"192.168.xx.xx\","
            +
            "\"Notify group\" :\"4\"}]";

    @BeforeEach
    public void initDingTalkConfig() {
        // Just for this test, I will delete these configurations before this PR is merged
        weChatConfig.put(WeChatAlertParamsConstants.NAME_ENTERPRISE_WE_CHAT_AGENT_ID, "100000");
        weChatConfig.put(WeChatAlertParamsConstants.NAME_ENTERPRISE_WE_CHAT_CORP_ID, "NAME_ENTERPRISE_WE_CHAT_CORP_ID");
        weChatConfig.put(WeChatAlertParamsConstants.NAME_ENTERPRISE_WE_CHAT_SECRET, "NAME_ENTERPRISE_WE_CHAT_SECRET");
        weChatConfig.put(WeChatAlertParamsConstants.NAME_ENTERPRISE_WE_CHAT_USERS, "Kris");
        weChatConfig.put(WeChatAlertParamsConstants.NAME_ENTERPRISE_WE_CHAT_TEAM_SEND_MSG, "msg");
        weChatConfig.put(AlertConstants.NAME_SHOW_TYPE, ShowType.TABLE.getDescp());
    }

    @Test
    public void testSendWeChatTableMsg() {
        WeChatSender weChatSender = new WeChatSender(weChatConfig);

        AlertResult alertResult = weChatSender.sendEnterpriseWeChat("test", content);
        Assertions.assertFalse(alertResult.isSuccess());
    }

    @Test
    public void testSendWeChatTextMsg() {
        weChatConfig.put(AlertConstants.NAME_SHOW_TYPE, ShowType.TEXT.getDescp());
        WeChatSender weChatSender = new WeChatSender(weChatConfig);
        AlertResult alertResult = weChatSender.sendEnterpriseWeChat("test", content);
        Assertions.assertFalse(alertResult.isSuccess());
    }

}
