package io.yak.ops.plugin.alert.email;

import io.yak.ops.alert.api.AlertChannel;
import io.yak.ops.alert.api.AlertData;
import io.yak.ops.alert.api.AlertInfo;
import io.yak.ops.alert.api.AlertResult;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class EmailAlertChannel implements AlertChannel {

    @Override
    public AlertResult process(AlertInfo info) {

        AlertData alert = info.getAlertData();
        Map<String, String> paramsMap = info.getAlertParams();
        if (null == paramsMap) {
            return new AlertResult(false, "mail params is null");
        }
        MailSender mailSender = new MailSender(paramsMap);
        AlertResult alertResult = mailSender.sendMails(alert.getTitle(), alert.getContent());

        if (alertResult == null) {
            alertResult = new AlertResult();
            alertResult.setSuccess(false);
            alertResult.setMessage("alert send error.");
            log.info("alert send error : {}", alertResult.getMessage());
            return alertResult;
        }

        if (alertResult.isSuccess()) {
            log.info("alert send success");
            alertResult.setMessage("email send success.");
        } else {
            alertResult.setMessage("alert send error.");
            log.info("alert send error : {}", alertResult.getMessage());
        }

        return alertResult;
    }
}
