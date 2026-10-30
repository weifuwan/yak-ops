package io.yak.ops.composer;

import io.yak.ops.composer.definition.PipelineDef;

/**
 * Translates a validated pipeline definition into a deferred execution.
 *
 * <p>Following Flink CDC 3.7's PipelineComposer boundary, this interface neither parses JSON
 * nor registers, submits, cancels or tracks running jobs. Yak Ops additionally passes
 * a trusted Task identity and an authorized admission plan to its embedded implementation.
 */
public interface PipelineComposer {

    /** Resolves authorized metadata and admission claims without starting physical work. */
    PipelinePlan plan(PipelineDef pipelineDef, PipelineContext context);

    /** Composes an execution without starting it; Task registration precedes start(). */
    PipelineExecution compose(PipelineDef pipelineDef, PipelinePlan plan, PipelineContext context);
}
