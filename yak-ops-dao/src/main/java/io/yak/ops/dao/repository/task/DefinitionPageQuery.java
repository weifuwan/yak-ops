package io.yak.ops.dao.repository.task;

/** Workspace-scoped Task filtering over name, type, and publication status. */
public record DefinitionPageQuery(
        String workspaceId, int pageNo, int pageSize, String keyword, String taskType, Integer statusValue) {}
