package io.yak.ops.alert.service;

import io.yak.ops.alert.api.AlertData;
import io.yak.ops.alert.api.AlertResult;
import io.yak.ops.alert.config.AlertConfig;
import io.yak.ops.alert.plugin.AlertPluginManager;
import io.yak.ops.common.enums.AlertStatus;
import io.yak.ops.dao.AlertDao;
import io.yak.ops.dao.entity.Alert;
import io.yak.ops.dao.entity.AlertPluginInstance;
import io.yak.ops.extract.alert.request.AlertSendResponse;

import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AlertSender extends AbstractEventSender<Alert> {

    private final AlertDao alertDao;

    public AlertSender(AlertDao alertDao,
                       AlertPluginManager alertPluginManager,
                       AlertConfig alertConfig) {
        super(alertPluginManager, alertConfig.getWaitTimeout());
        this.alertDao = alertDao;
    }

    /**
     * sync send alert handler
     *
     * @param alertGroupId alertGroupId
     * @param title        title
     * @param content      content
     * @return AlertSendResponseCommand
     */
    public AlertSendResponse syncHandler(int alertGroupId, String title, String content) {
        List<AlertPluginInstance> alertInstanceList = alertDao.listInstanceByAlertGroupId(alertGroupId);
        AlertData alertData = AlertData.builder()
                .content(content)
                .title(title)
                .build();

        boolean sendResponseStatus = true;
        List<AlertSendResponse.AlertSendResponseResult> sendResponseResults = new ArrayList<>();

        if (CollectionUtils.isEmpty(alertInstanceList)) {
            AlertSendResponse.AlertSendResponseResult alertSendResponseResult =
                    new AlertSendResponse.AlertSendResponseResult();
            String message = String.format("Alert GroupId %s send error : not found alert instance", alertGroupId);
            alertSendResponseResult.setSuccess(false);
            alertSendResponseResult.setMessage(message);
            sendResponseResults.add(alertSendResponseResult);
            log.error("Alert GroupId {} send error : not found alert instance", alertGroupId);
            return new AlertSendResponse(false, sendResponseResults);
        }

        for (AlertPluginInstance instance : alertInstanceList) {
            AlertResult alertResult = doSendEvent(instance, alertData);
            if (alertResult != null) {
                AlertSendResponse.AlertSendResponseResult alertSendResponseResult =
                        new AlertSendResponse.AlertSendResponseResult(
                                alertResult.isSuccess(),
                                alertResult.getMessage());
                sendResponseStatus = sendResponseStatus && alertSendResponseResult.isSuccess();
                sendResponseResults.add(alertSendResponseResult);
            }
        }

        return new AlertSendResponse(sendResponseStatus, sendResponseResults);
    }

    @Override
    public List<AlertPluginInstance> getAlertPluginInstanceList(Alert event) {
        return alertDao.listInstanceByAlertGroupId(event.getAlertGroupId());
    }

    @Override
    public AlertData getAlertData(Alert event) {
        return AlertData.builder()
                .id(event.getId())
                .content(event.getContent())
                .log(event.getLog())
                .title(event.getTitle())
                .alertType(event.getAlertType().getCode())
                .build();
    }

    @Override
    public Integer getEventId(Alert event) {
        return event.getId();
    }

    @Override
    public void onError(Alert event, String log) {
        alertDao.updateAlert(AlertStatus.EXECUTION_FAILURE, log, event.getId());
    }

    @Override
    public void onPartialSuccess(Alert event, String log) {
        alertDao.updateAlert(AlertStatus.EXECUTION_PARTIAL_SUCCESS, log, event.getId());
    }

    @Override
    public void onSuccess(Alert event, String log) {
        alertDao.updateAlert(AlertStatus.EXECUTION_SUCCESS, log, event.getId());
    }
}
