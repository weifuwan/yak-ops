package io.yak.ops.dao.repository.task;

import java.util.Map;

/** Workspace-scoped Task filtering over indexed parameters and the derived Data Sync product profile. */
public record DefinitionPageQuery(
        String workspaceId,
        int pageNo,
        int pageSize,
        String keyword,
        String taskType,
        Integer statusValue,
        Map<String, String> parameterFilters) {}
