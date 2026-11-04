package io.yak.ops.extract.worker.transportor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Deprecated
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskInstanceTriggerSavepointResponse {

    public boolean success;

    public String message;

    public static TaskInstanceTriggerSavepointResponse success() {
        return new TaskInstanceTriggerSavepointResponse(true, null);
    }

    public static TaskInstanceTriggerSavepointResponse fail(String message) {
        return new TaskInstanceTriggerSavepointResponse(false, message);
    }

}
