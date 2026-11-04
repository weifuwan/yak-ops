package io.yak.ops.plugin.alert.dingtalk;

import io.yak.ops.alert.api.AlertChannel;
import io.yak.ops.alert.api.AlertData;
import io.yak.ops.alert.api.AlertInfo;
import io.yak.ops.alert.api.AlertResult;

import java.util.Map;

public final class DingTalkAlertChannel implements AlertChannel {

    @Override
    public AlertResult process(AlertInfo alertInfo) {
        AlertData alertData = alertInfo.getAlertData();
        Map<String, String> paramsMap = alertInfo.getAlertParams();
        if (null == paramsMap) {
            return new AlertResult(false, "ding talk params is null");
        }
        return new DingTalkSender(paramsMap).sendDingTalkMsg(alertData.getTitle(), alertData.getContent());
    }
}
