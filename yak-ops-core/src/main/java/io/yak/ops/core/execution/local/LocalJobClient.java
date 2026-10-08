package io.yak.ops.core.execution.local;

import io.yak.ops.core.api.RuntimeExecutionMode;
import io.yak.ops.core.api.common.JobExecutionResult;
import io.yak.ops.core.api.common.JobID;
import io.yak.ops.core.api.common.JobStatus;
import io.yak.ops.core.configuration.Configuration;
import io.yak.ops.core.execution.JobClient;
import io.yak.ops.core.graph.StreamGraph;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 单次本地作业的运行句柄。
 *
 * <p>提交后立即可用；状态可查询，结果异步完成。
 * 取消请求会发出线程中断，但直到 Runner 结束并完成资源清理后才进入 CANCELED。
 *
 * @author weifuwan
 */
public final class LocalJobClient implements JobClient {

    private final JobID jobID;
    private final Object monitor = new Object();
    private final CompletableFuture<JobExecutionResult> result = new CompletableFuture<>();
    private final CompletableFuture<Void> cancellation = new CompletableFuture<>();

    private volatile JobStatus status = JobStatus.CREATED;
    private volatile boolean cancellationRequested;
    private Thread worker;

    LocalJobClient(JobID jobID) {
        this.jobID = Objects.requireNonNull(jobID, "jobID 不能为空");
    }

    /** 提交工作线程。该方法只允许被 LocalPipelineExecutor 调用一次。 */
    void start(StreamGraph graph, RuntimeExecutionMode mode, Configuration configuration, LocalJobRunner runner) {
        Objects.requireNonNull(graph, "graph 不能为空");
        Objects.requireNonNull(mode, "mode 不能为空");
        Objects.requireNonNull(configuration, "configuration 不能为空");
        Objects.requireNonNull(runner, "runner 不能为空");

        Thread thread = Thread.ofVirtual()
                .name("yak-local-job-" + jobID)
                .unstarted(() -> runJob(graph, mode, configuration, runner));
        synchronized (monitor) {
            if (worker != null) {
                throw new IllegalStateException("同一 JobClient 不能重复提交");
            }
            worker = thread;
        }
        thread.start();
    }

    private void runJob(
            StreamGraph graph,
            RuntimeExecutionMode mode,
            Configuration configuration,
            LocalJobRunner runner) {
        synchronized (monitor) {
            if (!cancellationRequested) {
                status = JobStatus.RUNNING;
            }
        }
        if (cancellationRequested) {
            completeCanceled();
            return;
        }

        long startedNanos = System.nanoTime();
        try {
            runner.run(graph, mode, configuration, () -> cancellationRequested);
            if (!graph.isBounded() && !cancellationRequested) {
                completeFailed(new IllegalStateException("无界 Pipeline 未被取消却提前结束"));
                return;
            }
            completeNormally(TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - startedNanos)));
        } catch (Throwable failure) {
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (cancellationRequested
                    && (failure instanceof InterruptedException || failure instanceof CancellationException)) {
                completeCanceled();
            } else {
                completeFailed(failure);
            }
        }
    }

    private void completeNormally(long durationMillis) {
        boolean canceled;
        synchronized (monitor) {
            canceled = cancellationRequested;
            status = canceled ? JobStatus.CANCELED : JobStatus.FINISHED;
        }
        if (canceled) {
            notifyCanceled();
        } else {
            result.complete(new JobExecutionResult(jobID, durationMillis));
        }
    }

    private void completeCanceled() {
        synchronized (monitor) {
            status = JobStatus.CANCELED;
        }
        notifyCanceled();
    }

    private void notifyCanceled() {
        result.completeExceptionally(new CancellationException("作业已取消：" + jobID));
        cancellation.complete(null);
    }

    private void completeFailed(Throwable failure) {
        boolean wasCancelRequested;
        synchronized (monitor) {
            status = JobStatus.FAILED;
            wasCancelRequested = cancellationRequested;
        }
        result.completeExceptionally(failure);
        if (wasCancelRequested) {
            cancellation.completeExceptionally(failure);
        }
    }

    @Override
    public JobID getJobID() {
        return jobID;
    }

    /** 获取调用时刻的状态快照，不等待状态变化。 */
    @Override
    public CompletableFuture<JobStatus> getJobStatus() {
        return CompletableFuture.completedFuture(status);
    }

    /**
     * 请求取消。Future 在运行线程退出后完成，而非发出中断时立即完成。
     * 已正常完成或失败的作业不允许被重新标记为取消。
     */
    @Override
    public CompletableFuture<Void> cancel() {
        Thread thread;
        synchronized (monitor) {
            if (status == JobStatus.CANCELED) {
                return CompletableFuture.completedFuture(null);
            }
            if (status == JobStatus.FINISHED || status == JobStatus.FAILED) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("作业已结束，无法取消：" + status));
            }
            if (cancellationRequested) {
                return cancellation.copy();
            }
            cancellationRequested = true;
            status = JobStatus.CANCELLING;
            thread = worker;
        }
        if (thread != null) {
            thread.interrupt();
        }
        return cancellation.copy();
    }

    /** 返回结果 Future 的副本，避免调用方修改内部完成状态。 */
    @Override
    public CompletableFuture<JobExecutionResult> getJobExecutionResult() {
        return result.copy();
    }
}
