package io.yak.ops.core.api.connector.sink;

import io.yak.ops.core.api.common.TaskInfo;
import io.yak.ops.core.configuration.Configuration;

/** Read-only runtime context supplied to each Sink V2 writer attempt. */
public interface WriterInitContext {

    TaskInfo getTaskInfo();

    /** Returns an independent copy of the effective job configuration. */
    Configuration getConfiguration();
}
