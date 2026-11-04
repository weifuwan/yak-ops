package io.yak.ops.common.model;

import io.yak.ops.common.enums.ServerStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class BaseHeartBeat implements HeartBeat {

    protected int processId;
    protected long startupTime;
    protected long reportTime;
    protected double jvmCpuUsage;
    protected double cpuUsage;
    protected double jvmMemoryUsage;
    private double jvmHeapUsed;
    private double jvmNonHeapUsed;
    private double jvmHeapMax;
    private double jvmNonHeapMax;
    protected double memoryUsage;
    protected double diskUsage;
    protected ServerStatus serverStatus;

    protected String host;
    protected int port;

}
