package io.yak.ops.core.parser;

import io.yak.ops.core.definition.PipelineDef;

/** Parses an immutable pipeline definition without opening connections or executing jobs. */
public interface PipelineDefinitionParser {
    PipelineDef parse(String content);
}
