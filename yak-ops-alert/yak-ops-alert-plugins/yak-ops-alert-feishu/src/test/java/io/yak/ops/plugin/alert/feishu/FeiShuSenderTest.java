package io.yak.ops.plugin.alert.feishu;

import io.yak.ops.alert.api.AlertData;
import io.yak.ops.alert.api.AlertResult;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FeiShuSenderTest {

    private static Map<String, String> feiShuConfig = new HashMap<>();

    @BeforeEach
    public void initFeiShuConfig() {
        feiShuConfig.put(FeiShuParamsConstants.WEB_HOOK, "https://open.feishu.cn/open-apis/bot/v2/hook/xxxxx");
    }

    @Test
    public void testSend() {
        AlertData alertData = new AlertData();
        alertData.setTitle("feishu test title");
        alertData.setContent("feishu test content");
        FeiShuSender feiShuSender = new FeiShuSender(feiShuConfig);
        AlertResult alertResult = feiShuSender.sendFeiShuMsg(alertData);
        Assertions.assertFalse(alertResult.isSuccess());
    }

}
