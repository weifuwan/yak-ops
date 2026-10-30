package io.yak.ops.core.client;

import io.yak.ops.core.definition.PipelineDef;

/**
 * Host-provided pipeline planner and runner. Core does not depend on YakFlow runtime,
 * workspace persistence, Spring, or individual connectors.
 */
public interface DataSyncEngine {

    void validate(PipelineDef definition, DataSyncContext context);

    DataSyncPlan plan(PipelineDef definition, DataSyncContext context);

    /** Creates a deferred logical submission; must not start physical work. */
    DataSyncJob prepare(PipelineDef definition, DataSyncPlan plan, DataSyncContext context);
}
