package io.yak.ops.plugin.task.datasync;

import io.yak.ops.composer.PipelineComposer;
import io.yak.ops.connector.base.factory.FactoryUtil;
import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskChannelFactory;
import java.util.Objects;

/** Registers the DATA_SYNC Task boundary with the shared Connector factories and Pipeline composer. */
public final class PipelineTaskChannelFactory implements TaskChannelFactory {
    private final FactoryUtil factories;
    private final PipelineComposer composer;

    public PipelineTaskChannelFactory(FactoryUtil factories, PipelineComposer composer) {
        this.factories = Objects.requireNonNull(factories, "factories");
        this.composer = Objects.requireNonNull(composer, "composer");
    }

    @Override
    public String getName() {
        return PipelineTaskChannel.TYPE;
    }

    @Override
    public TaskChannel create() {
        return new PipelineTaskChannel(factories, composer);
    }
}
