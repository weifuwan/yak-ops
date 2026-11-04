package io.yak.ops.extract.master.transportor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskGroupSlotAcquireSuccessNotifyRequest {

    private Integer workflowInstanceId;

    private Integer taskInstanceId;

}
