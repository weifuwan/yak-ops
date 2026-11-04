package io.yak.ops.alert.rpc;

import io.yak.ops.alert.service.AlertSender;
import io.yak.ops.extract.alert.IAlertOperator;
import io.yak.ops.extract.alert.request.AlertSendRequest;
import io.yak.ops.extract.alert.request.AlertSendResponse;
import io.yak.ops.extract.alert.request.AlertTestSendRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AlertOperatorImpl implements IAlertOperator {

    @Autowired
    private AlertSender alertSender;

    @Override
    public AlertSendResponse sendAlert(AlertSendRequest alertSendRequest) {
        log.info("Received AlertSendRequest : {}", alertSendRequest);
        AlertSendResponse alertSendResponse = alertSender.syncHandler(
                alertSendRequest.getGroupId(),
                alertSendRequest.getTitle(),
                alertSendRequest.getContent());
        log.info("Handle AlertSendRequest finish: {}", alertSendResponse);
        return alertSendResponse;
    }

    @Override
    public AlertSendResponse sendTestAlert(AlertTestSendRequest alertSendRequest) {
        log.info("Received AlertTestSendRequest : {}", alertSendRequest);
        AlertSendResponse alertSendResponse = alertSender.syncTestSend(
                alertSendRequest.getPluginDefineId(),
                alertSendRequest.getPluginInstanceParams());
        log.info("Handle AlertTestSendRequest finish: {}", alertSendResponse);
        return alertSendResponse;
    }
}
