package org.apache.flink.api.connector.sink2;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.JobInfo;
import org.apache.flink.api.common.TaskInfo;
import org.apache.flink.api.common.operators.MailboxExecutor;
import org.apache.flink.api.common.operators.ProcessingTimeService;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.metrics.groups.SinkWriterMetricGroup;
import org.apache.flink.util.UserCodeClassLoader;

import java.util.OptionalLong;

/**
 * Compatibility adapter for Flink 2.2. This class is part of the multi-version compatibility layer
 * that allows Flink CDC to work across different Flink versions.
 */
@Internal
public class InitContextAdapter implements Sink.InitContext {
    private final WriterInitContext context;

    public InitContextAdapter(WriterInitContext context) {
        this.context = context;
    }

    public UserCodeClassLoader getUserCodeClassLoader() {
        return this.context.getUserCodeClassLoader();
    }

    public MailboxExecutor getMailboxExecutor() {
        return this.context.getMailboxExecutor();
    }

    public ProcessingTimeService getProcessingTimeService() {
        return this.context.getProcessingTimeService();
    }

    public int getSubtaskId() {
        return this.context.getTaskInfo().getIndexOfThisSubtask();
    }

    public int getNumberOfParallelSubtasks() {
        return this.context.getTaskInfo().getNumberOfParallelSubtasks();
    }

    public SinkWriterMetricGroup metricGroup() {
        return this.context.metricGroup();
    }

    public OptionalLong getRestoredCheckpointId() {
        return this.context.getRestoredCheckpointId();
    }

    @Override
    public JobInfo getJobInfo() {
        return this.context.getJobInfo();
    }

    @Override
    public TaskInfo getTaskInfo() {
        return this.context.getTaskInfo();
    }

    public SerializationSchema.InitializationContext asSerializationSchemaInitializationContext() {
        return this.context.asSerializationSchemaInitializationContext();
    }

    @Override
    public boolean isObjectReuseEnabled() {
        return this.context.isObjectReuseEnabled();
    }

    @Override
    public <IN> TypeSerializer<IN> createInputSerializer() {
        return this.context.createInputSerializer();
    }
}
