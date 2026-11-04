package io.yak.ops.extract.worker.transportor;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Deprecated
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskInstanceTriggerSavepointRequest {

    private int taskInstanceId;

}
