package io.yak.ops.common.bean.vo.task;

import com.fasterxml.jackson.annotation.JsonRawValue;

/** Immutable Task Definition version used by an authorized execution Instance. */
public record InstanceDefinitionVO(
        String definitionId,
        int version,
        String name,
        String taskType,
        int parameterSchemaVersion,
        @JsonRawValue String parameters) {}
