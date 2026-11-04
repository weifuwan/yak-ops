package io.yak.ops.alert.runner;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.alert.api.AlertChannel;
import io.yak.ops.alert.api.AlertResult;
import io.yak.ops.alert.config.AlertConfig;
import io.yak.ops.alert.plugin.AlertPluginManager;
import io.yak.ops.alert.service.AlertSender;
import io.yak.ops.common.enums.AlertStatus;
import io.yak.ops.common.enums.AlertType;
import io.yak.ops.common.enums.WarningType;
import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.dao.AlertDao;
import io.yak.ops.dao.PluginDao;
import io.yak.ops.dao.entity.Alert;
import io.yak.ops.dao.entity.AlertPluginInstance;
import io.yak.ops.extract.alert.request.AlertSendResponse;
import io.yak.ops.spi.params.PluginParamsTransfer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class AlertSenderTest {

    private static final Logger logger = LoggerFactory.getLogger(AlertSenderTest.class);

    @Mock
    private AlertDao alertDao;
    @Mock
    private PluginDao pluginDao;
    @Mock
    private AlertPluginManager alertPluginManager;
    @Mock
    private AlertConfig alertConfig;

    @InjectMocks
    private AlertSender alertSender;

    private static final String PLUGIN_INSTANCE_PARAMS =
            "{\"User\":\"xx\",\"receivers\":\"xx\",\"sender\":\"xx\",\"smtpSslTrust\":\"*\",\"enableSmtpAuth\":\"true\",\"receiverCcs\":null,\"showType\":\"table\",\"starttlsEnable\":\"false\",\"serverPort\":\"25\",\"serverHost\":\"xx\",\"Password\":\"xx\",\"sslEnable\":\"false\"}";

    private static final String PLUGIN_INSTANCE_NAME = "alert-instance-mail";
    private static final String TITLE = "alert mail test TITLE";
    private static final String CONTENT = "alert mail test CONTENT";

    private static final int PLUGIN_DEFINE_ID = 1;

    private static final int ALERT_GROUP_ID = 1;

    @Test
    void testSyncHandler() {
        // 1.alert instance does not exist
        when(alertDao.listInstanceByAlertGroupId(ALERT_GROUP_ID)).thenReturn(null);

        AlertSendResponse alertSendResponse = alertSender.syncHandler(ALERT_GROUP_ID, TITLE, CONTENT);
        Assertions.assertFalse(alertSendResponse.isSuccess());
        alertSendResponse.getResResults().forEach(result -> logger
                .info("alert send response result, status:{}, message:{}", result.isSuccess(), result.getMessage()));

        // 2.alert plugin does not exist
        int pluginDefineId = 1;
        String pluginInstanceName = "alert-instance-mail";
        List<AlertPluginInstance> alertInstanceList = new ArrayList<>();
        AlertPluginInstance alertPluginInstance = new AlertPluginInstance(
                pluginDefineId, PLUGIN_INSTANCE_PARAMS, pluginInstanceName);
        alertPluginInstance.setId(1);
        alertInstanceList.add(alertPluginInstance);
        when(alertDao.listInstanceByAlertGroupId(1)).thenReturn(alertInstanceList);

        alertSendResponse = alertSender.syncHandler(ALERT_GROUP_ID, TITLE, CONTENT);
        Assertions.assertFalse(alertSendResponse.isSuccess());
        alertSendResponse.getResResults().forEach(result -> logger
                .info("alert send response result, status:{}, message:{}", result.isSuccess(), result.getMessage()));

        // 3.alert result value is null
        AlertChannel alertChannelMock = mock(AlertChannel.class);
        when(alertChannelMock.process(Mockito.any())).thenReturn(null);
        when(alertPluginManager.getAlertChannel(1)).thenReturn(Optional.of(alertChannelMock));

        alertSendResponse = alertSender.syncHandler(ALERT_GROUP_ID, TITLE, CONTENT);
        Assertions.assertFalse(alertSendResponse.isSuccess());
        alertSendResponse.getResResults().forEach(result -> logger
                .info("alert send response result, status:{}, message:{}", result.isSuccess(), result.getMessage()));

        // 4.abnormal information inside the alert plug-in code
        AlertResult alertResult = new AlertResult();
        alertResult.setSuccess(false);
        alertResult.setMessage("Abnormal information inside the alert plug-in code");
        when(alertChannelMock.process(Mockito.any())).thenReturn(alertResult);
        when(alertPluginManager.getAlertChannel(1)).thenReturn(Optional.of(alertChannelMock));

        alertSendResponse = alertSender.syncHandler(ALERT_GROUP_ID, TITLE, CONTENT);
        Assertions.assertFalse(alertSendResponse.isSuccess());
        alertSendResponse.getResResults().forEach(result -> logger
                .info("alert send response result, status:{}, message:{}", result.isSuccess(), result.getMessage()));

        // 5.alert plugin send success
        alertResult = new AlertResult();
        alertResult.setSuccess(true);
        alertResult.setMessage(String.format("Alert Plugin %s send success", pluginInstanceName));
        when(alertChannelMock.process(Mockito.any())).thenReturn(alertResult);
        when(alertPluginManager.getAlertChannel(1)).thenReturn(Optional.of(alertChannelMock));

        alertSendResponse = alertSender.syncHandler(ALERT_GROUP_ID, TITLE, CONTENT);
        Assertions.assertTrue(alertSendResponse.isSuccess());
        alertSendResponse.getResResults().forEach(result -> logger
                .info("alert send response result, status:{}, message:{}", result.isSuccess(), result.getMessage()));

    }

    @Test
    void testRun() {
        Alert alert = new Alert();
        alert.setId(1);
        alert.setAlertGroupId(ALERT_GROUP_ID);
        alert.setTitle(TITLE);
        alert.setContent(CONTENT);
        alert.setWarningType(WarningType.FAILURE);
        alert.setAlertType(AlertType.TASK_FAILURE);

        List<AlertPluginInstance> alertInstanceList = new ArrayList<>();
        when(alertDao.listInstanceByAlertGroupId(ALERT_GROUP_ID)).thenReturn(alertInstanceList);

        // 1. alert plugin send success
        AlertPluginInstance alertPluginInstance = new AlertPluginInstance(
                PLUGIN_DEFINE_ID, PLUGIN_INSTANCE_PARAMS, PLUGIN_INSTANCE_NAME);
        alertPluginInstance.setId(alertPluginInstance.getPluginDefineId());
        alertInstanceList.add(alertPluginInstance);

        AlertChannel alertChannelMock = mock(AlertChannel.class);
        when(alertPluginManager.getAlertChannel(PLUGIN_DEFINE_ID)).thenReturn(Optional.of(alertChannelMock));
        AlertResult alertSuccessResult = AlertResult.success();
        when(alertChannelMock.process(Mockito.any())).thenReturn(alertSuccessResult);
        alertSender.sendEvent(alert);
        verify(alertDao).updateAlert(eq(AlertStatus.EXECUTION_SUCCESS), anyString(), anyInt());

        // 2. alert plugin send failed
        AlertPluginInstance otherAlertPluginInstance = new AlertPluginInstance(
                PLUGIN_DEFINE_ID + 1, PLUGIN_INSTANCE_PARAMS, PLUGIN_INSTANCE_NAME);
        otherAlertPluginInstance.setId(otherAlertPluginInstance.getPluginDefineId());
        alertInstanceList.clear();
        alertInstanceList.add(otherAlertPluginInstance);

        AlertChannel otherAlertChannelMock = mock(AlertChannel.class);
        when(alertPluginManager.getAlertChannel(PLUGIN_DEFINE_ID + 1)).thenReturn(Optional.of(otherAlertChannelMock));
        AlertResult alertFailedResult =
                AlertResult.fail(String.format("Alert Plugin %s send failed", PLUGIN_INSTANCE_NAME));
        when(otherAlertChannelMock.process(Mockito.any())).thenReturn(alertFailedResult);
        alertSender.sendEvent(alert);
        verify(alertDao).updateAlert(eq(AlertStatus.EXECUTION_FAILURE), anyString(), anyInt());

        // 3. alert plugin send partial success
        alertInstanceList.clear();
        alertInstanceList.add(alertPluginInstance);
        alertInstanceList.add(otherAlertPluginInstance);
        alertSender.sendEvent(alert);
        verify(alertDao).updateAlert(eq(AlertStatus.EXECUTION_PARTIAL_SUCCESS), anyString(), anyInt());

    }

    @Test
    void testSendAlert() {
        AlertResult sendResult = new AlertResult();
        sendResult.setSuccess(true);
        sendResult.setMessage(String.format("Alert Plugin %s send success", PLUGIN_INSTANCE_NAME));
        AlertChannel alertChannelMock = mock(AlertChannel.class);
        when(alertChannelMock.process(Mockito.any())).thenReturn(sendResult);
        when(alertPluginManager.getAlertChannel(1)).thenReturn(Optional.of(alertChannelMock));
        Map<String, String> paramsMap = JSONUtils.toMap(PLUGIN_INSTANCE_PARAMS);
        MockedStatic<PluginParamsTransfer> pluginParamsTransferMockedStatic =
                Mockito.mockStatic(PluginParamsTransfer.class);
        pluginParamsTransferMockedStatic.when(() -> PluginParamsTransfer.getPluginParamsMap(PLUGIN_INSTANCE_PARAMS))
                .thenReturn(paramsMap);
        alertSender.syncTestSend(PLUGIN_DEFINE_ID, PLUGIN_INSTANCE_PARAMS);
    }
}
