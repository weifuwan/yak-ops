package io.yak.ops.extract.master.transportor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskGroupSlotAcquireSuccessNotifyResponse {

    private boolean success;

    private String message;

    public static TaskGroupSlotAcquireSuccessNotifyResponse success() {
        return new TaskGroupSlotAcquireSuccessNotifyResponse(true, null);
    }

    public static TaskGroupSlotAcquireSuccessNotifyResponse failed(String message) {
        return new TaskGroupSlotAcquireSuccessNotifyResponse(false, message);
    }
}
