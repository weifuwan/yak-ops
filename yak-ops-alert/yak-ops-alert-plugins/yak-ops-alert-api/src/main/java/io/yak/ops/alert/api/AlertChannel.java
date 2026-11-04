package io.yak.ops.alert.api;

/**
 * alert channel for sending alerts
 */
public interface AlertChannel {

    /**
     * process and send alert
     *
     * @param info alert info
     * @return process alarm result
     */
    AlertResult process(AlertInfo info);

}
