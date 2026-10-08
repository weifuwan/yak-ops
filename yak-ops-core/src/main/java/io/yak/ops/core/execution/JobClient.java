package io.yak.ops.core.execution;

import io.yak.ops.core.api.common.JobExecutionResult;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.common.JobStatus;
import java.util.concurrent.CompletableFuture;

/** 与单次提交的批处理或流处理作业关联的客户端。 */
public interface JobClient {

    JobID getJobID();

    CompletableFuture<JobStatus> getJobStatus();

    /** 取消操作到达终态时完成；取消失败时异常完成。 */
    CompletableFuture<Void> cancel();

    /** 仅当作业为 FINISHED 时正常完成；FAILED 或 CANCELED 时异常完成。 */
    CompletableFuture<JobExecutionResult> getJobExecutionResult();
}
