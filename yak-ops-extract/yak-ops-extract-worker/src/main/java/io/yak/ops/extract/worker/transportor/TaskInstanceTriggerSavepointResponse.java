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

    public String savepointPath;

    public static TaskInstanceTriggerSavepointResponse success() {
        return new TaskInstanceTriggerSavepointResponse(true, null, null);
    }

    public static TaskInstanceTriggerSavepointResponse success(String savepointPath) {
        return new TaskInstanceTriggerSavepointResponse(true, null, savepointPath);
    }

    public static TaskInstanceTriggerSavepointResponse fail(String message) {
        return new TaskInstanceTriggerSavepointResponse(false, message, null);
    }

}
