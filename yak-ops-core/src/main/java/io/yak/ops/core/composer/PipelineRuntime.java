package io.yak.ops.core.composer;

import io.yak.ops.core.definition.PipelineDef;

/**
 * Host-provided pipeline planner and runner. Core does not depend on YakFlow runtime,
 * workspace persistence, Spring, or individual connectors.
 */
public interface PipelineRuntime {

    void validate(PipelineDef definition, PipelineContext context);

    PipelinePlan plan(PipelineDef definition, PipelineContext context);

    /** Creates a deferred logical submission; must not start physical work. */
    PipelineExecution prepare(PipelineDef definition, PipelinePlan plan, PipelineContext context);
}
