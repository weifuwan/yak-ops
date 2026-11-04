package io.yak.ops.extract.alert.request;

import java.io.Serializable;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertSendResponse {

    /**
     * true:All alert are successful,
     * false:As long as one alert fails
     */
    private boolean success;

    private List<AlertSendResponseResult> resResults;

    public static AlertSendResponse success(List<AlertSendResponseResult> resResults) {
        return new AlertSendResponse(true, resResults);
    }

    public static AlertSendResponse fail(List<AlertSendResponseResult> resResults) {
        return new AlertSendResponse(false, resResults);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertSendResponseResult implements Serializable {

        private boolean success;

        private String message;

        public static AlertSendResponseResult success() {
            return new AlertSendResponseResult(true, null);
        }

        public static AlertSendResponseResult fail(String message) {
            return new AlertSendResponseResult(false, message);
        }

    }

}
