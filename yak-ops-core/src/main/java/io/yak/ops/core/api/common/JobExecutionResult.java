package io.yak.ops.core.api.common;

import java.util.Objects;

/** 单次作业成功完成后的结果；失败或取消的作业不产生成功结果。 */
public final class JobExecutionResult {

    private final JobID jobID;
    private final long netRuntime;

    /** @param netRuntime 实际运行耗时（毫秒），不包含提交准备阶段 */
    public JobExecutionResult(JobID jobID, long netRuntime) {
        this.jobID = Objects.requireNonNull(jobID, "jobID must not be null");
        if (netRuntime < 0) {
            throw new IllegalArgumentException("netRuntime must not be negative");
        }
        this.netRuntime = netRuntime;
    }

    public JobID getJobID() {
        return jobID;
    }

    public long getNetRuntime() {
        return netRuntime;
    }
}
