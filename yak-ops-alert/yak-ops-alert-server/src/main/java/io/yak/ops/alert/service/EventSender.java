package io.yak.ops.alert.service;

import io.yak.ops.alert.api.AlertData;
import io.yak.ops.alert.api.AlertResult;
import io.yak.ops.dao.entity.AlertPluginInstance;
import io.yak.ops.extract.alert.request.AlertSendResponse;

public interface EventSender<T> {

    void sendEvent(T event);

    AlertResult doSendEvent(AlertPluginInstance instance, AlertData alertData);

    AlertSendResponse syncTestSend(int pluginDefineId, String pluginInstanceParams);

}
