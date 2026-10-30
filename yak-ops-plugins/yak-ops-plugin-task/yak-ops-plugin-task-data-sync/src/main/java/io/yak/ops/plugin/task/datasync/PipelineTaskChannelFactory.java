package io.yak.ops.plugin.task.datasync;

import io.yak.ops.plugin.task.api.TaskChannel;
import io.yak.ops.plugin.task.api.TaskChannelFactory;
import io.yak.ops.sync.runtime.PipelineService;
import java.util.Objects;

/** Factory registers the DATA_SYNC TaskChannel; engine details remain behind PipelineService. */
public final class PipelineTaskChannelFactory implements TaskChannelFactory {
    private final PipelineService service;

    public PipelineTaskChannelFactory(PipelineService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public String getName() {
        return PipelineTaskChannel.TYPE;
    }

    @Override
    public TaskChannel create() {
        return new PipelineTaskChannel(service);
    }
}
