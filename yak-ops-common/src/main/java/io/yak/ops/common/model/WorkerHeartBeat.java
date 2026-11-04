package io.yak.ops.common.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
public class WorkerHeartBeat extends BaseHeartBeat implements HeartBeat {

    private int workerHostWeight; // worker host weight
    private double threadPoolUsage; // worker waiting task count
    private String workerGroup;
}
