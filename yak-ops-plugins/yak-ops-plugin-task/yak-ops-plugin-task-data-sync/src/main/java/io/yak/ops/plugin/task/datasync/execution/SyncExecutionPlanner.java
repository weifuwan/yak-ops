package io.yak.ops.plugin.task.datasync.execution;

import io.yak.ops.common.enums.datasync.DataSyncWriteMode;
import io.yak.ops.core.data.TableId;
import io.yak.ops.plugin.task.api.TaskPluginException;
import io.yak.ops.plugin.task.api.execution.ExecutionResource;
import io.yak.ops.plugin.task.api.execution.TaskExecutionContext;
import io.yak.ops.plugin.task.api.execution.TaskExecutionPlan;
import io.yak.ops.plugin.task.datasync.SyncParameters;
import io.yak.ops.plugin.task.datasync.SyncValidator;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeResources;
import io.yak.ops.plugin.task.datasync.resource.SyncRuntimeTable;
import io.yak.ops.plugin.task.datasync.sink.JdbcSinkParameters;
import io.yak.ops.plugin.task.datasync.source.MySqlCdcSourceParameters;
import io.yak.ops.plugin.task.datasync.source.SourceParameters;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Resolves Workspace-scoped DATA_SYNC tables and plans the exclusive target claim. */
public final class SyncExecutionPlanner {

    private final SyncRuntimeResources resources;

    public SyncExecutionPlanner(SyncRuntimeResources resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    /**
     * Declares a stable resource binding without physical execution effects.
     *
     * @param parameters pinned DATA_SYNC parameters
     * @param context trusted Task identity
     * @return canonical exclusive claim and binding fingerprint
     */
    public TaskExecutionPlan plan(SyncParameters parameters, TaskExecutionContext context) {
        return prepare(parameters, context).resourcePlan();
    }

    /**
     * Resolves source and target table metadata once for one planning pass.
     *
     * @param pipeline pinned DATA_SYNC parameters
     * @param context trusted Task identity
     * @return physical table metadata and non-secret resource binding
     */
    public SyncPipelinePlan prepare(SyncParameters pipeline, TaskExecutionContext context) {
        SyncValidator.checkParameters(pipeline);
        SourceParameters source = pipeline.source();
        JdbcSinkParameters sink = (JdbcSinkParameters) pipeline.sink();
        SyncRuntimeTable from = resources.table(
                context.workspaceId(), source.datasourceId(), source.database(), source.schema(), source.table());
        SyncRuntimeTable to = resources.table(
                context.workspaceId(), sink.datasourceId(), sink.database(), sink.schema(), sink.table());
        if (source instanceof MySqlCdcSourceParameters && !"MYSQL".equals(from.datasourceType())) {
            throw new TaskPluginException("MySQL CDC requires a MySQL datasource");
        }
        SyncValidator.checkSchemaCompatibility(
                from.schema(),
                to.schema(),
                source instanceof MySqlCdcSourceParameters || sink.writeMode() == DataSyncWriteMode.UPSERT);
        String target = encodePart(sink.datasourceId())
                + encodePart(to.table().catalog())
                + encodePart(to.table().schema())
                + encodePart(to.table().table());
        String binding = context.workspaceId()
                + "|" + source.datasourceId() + "|" + from.revision() + "|" + encodeTable(from.table())
                + "|" + sink.datasourceId() + "|" + to.revision() + "|" + encodeTable(to.table());
        TaskExecutionPlan plan =
                new TaskExecutionPlan(List.of(new ExecutionResource("DATA_SYNC_TARGET", target)), sha256(binding));
        return new SyncPipelinePlan(from, to, plan);
    }

    private static String encodeTable(TableId table) {
        return encodePart(table.catalog()) + encodePart(table.schema()) + encodePart(table.table());
    }

    private static String encodePart(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is required", error);
        }
    }
}
