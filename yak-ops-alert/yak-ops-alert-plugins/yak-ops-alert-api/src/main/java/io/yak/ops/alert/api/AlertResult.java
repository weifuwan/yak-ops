package io.yak.ops.alert.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@Data
@NoArgsConstructor
public class AlertResult {

    private boolean success;

    /**
     * alert result message, each plugin can have its own message
     */
    private String message;

    public static AlertResult success() {
        return new AlertResult(true, null);
    }

    public static AlertResult fail(String message) {
        return new AlertResult(false, message);
    }

}
