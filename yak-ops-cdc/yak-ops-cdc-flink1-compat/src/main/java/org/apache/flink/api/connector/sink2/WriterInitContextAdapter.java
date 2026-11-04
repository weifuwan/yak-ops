package org.apache.flink.api.connector.sink2;

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
 * Compatibility adapter for Flink 1.20. This class is part of the multi-version compatibility layer
 * that allows Flink CDC to work across different Flink versions.
 */
public class WriterInitContextAdapter implements WriterInitContext {

    private final Sink.InitContext context;

    public WriterInitContextAdapter(Sink.InitContext context) {
        this.context = context;
    }

    @Override
    public UserCodeClassLoader getUserCodeClassLoader() {
        return this.context.getUserCodeClassLoader();
    }

    @Override
    public MailboxExecutor getMailboxExecutor() {
        return this.context.getMailboxExecutor();
    }

    @Override
    public ProcessingTimeService getProcessingTimeService() {
        return this.context.getProcessingTimeService();
    }

    @Override
    public SinkWriterMetricGroup metricGroup() {
        return this.context.metricGroup();
    }

    @Override
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

    @Override
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
}
