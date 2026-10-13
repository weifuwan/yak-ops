package io.yak.ops.boot.integration.task;

import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import io.yak.ops.plugin.task.api.validation.TaskValidationContextFactory;
import jakarta.annotation.Resource;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Creates Workspace-bound Datasource validation for background and interactive Task operations. */
@Component
public class DataSourceTaskValidationContextFactory implements TaskValidationContextFactory {

    @Resource
    private DataSourceLookupService resources;

    @Override
    public TaskValidationContext forWorkspace(String workspaceId) {
        Objects.requireNonNull(workspaceId, "workspaceId");
        if (workspaceId.isBlank()) {
            throw new IllegalArgumentException("Workspace identity must not be blank");
        }
        return new WorkspaceTaskValidationContext(workspaceId, resources);
    }
}
