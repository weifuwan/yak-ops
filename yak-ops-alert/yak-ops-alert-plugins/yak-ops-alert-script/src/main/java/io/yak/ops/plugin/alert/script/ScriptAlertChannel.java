package io.yak.ops.plugin.alert.script;

import io.yak.ops.alert.api.AlertChannel;
import io.yak.ops.alert.api.AlertData;
import io.yak.ops.alert.api.AlertInfo;
import io.yak.ops.alert.api.AlertResult;

import org.apache.commons.collections4.MapUtils;

import java.util.Map;

public final class ScriptAlertChannel implements AlertChannel {

    @Override
    public AlertResult process(AlertInfo alertinfo) {
        AlertData alertData = alertinfo.getAlertData();
        Map<String, String> paramsMap = alertinfo.getAlertParams();
        if (MapUtils.isEmpty(paramsMap)) {
            return new AlertResult(false, "script params is empty");
        }
        return new ScriptSender(paramsMap).sendScriptAlert(alertData.getTitle(), alertData.getContent());
    }
}
