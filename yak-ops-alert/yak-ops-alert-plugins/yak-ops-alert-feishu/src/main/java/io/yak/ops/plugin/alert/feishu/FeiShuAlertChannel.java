package io.yak.ops.plugin.alert.feishu;

import io.yak.ops.alert.api.AlertChannel;
import io.yak.ops.alert.api.AlertData;
import io.yak.ops.alert.api.AlertInfo;
import io.yak.ops.alert.api.AlertResult;

import java.util.Map;

public final class FeiShuAlertChannel implements AlertChannel {

    @Override
    public AlertResult process(AlertInfo alertInfo) {
        AlertData alertData = alertInfo.getAlertData();
        Map<String, String> paramsMap = alertInfo.getAlertParams();
        if (null == paramsMap) {
            return new AlertResult(false, "feishu params is null");
        }
        return new FeiShuSender(paramsMap).sendFeiShuMsg(alertData);
    }
}
