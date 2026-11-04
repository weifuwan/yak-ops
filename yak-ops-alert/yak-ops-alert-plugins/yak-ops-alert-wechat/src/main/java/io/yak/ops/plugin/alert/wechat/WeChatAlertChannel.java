package io.yak.ops.plugin.alert.wechat;

import io.yak.ops.alert.api.AlertChannel;
import io.yak.ops.alert.api.AlertData;
import io.yak.ops.alert.api.AlertInfo;
import io.yak.ops.alert.api.AlertResult;

import java.util.Map;

public final class WeChatAlertChannel implements AlertChannel {

    @Override
    public AlertResult process(AlertInfo info) {
        AlertData alertData = info.getAlertData();
        Map<String, String> paramsMap = info.getAlertParams();
        if (null == paramsMap) {
            return new AlertResult(false, "we chat params is null");
        }
        return new WeChatSender(paramsMap).sendEnterpriseWeChat(alertData.getTitle(), alertData.getContent());

    }
}
