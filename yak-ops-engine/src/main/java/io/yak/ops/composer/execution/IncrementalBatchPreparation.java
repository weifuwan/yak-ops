package io.yak.ops.composer.execution;

import io.yak.ops.composer.PipelinePlan;
import io.yak.ops.composer.definition.EndpointOptions;
import io.yak.ops.composer.definition.IncrementalConfig;
import io.yak.ops.composer.definition.PipelineOptions;
import io.yak.ops.composer.definition.TableBinding;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.jdbc.JdbcConnectionOptions;
import io.yak.ops.connector.jdbc.JdbcSourceOptions;
import io.yak.ops.connector.jdbc.source.JdbcIncrementalRange;
import io.yak.ops.connector.jdbc.source.JdbcIncrementalUpperBound;
import io.yak.ops.core.data.TableId;
import io.yak.ops.plugin.datasource.api.lookup.DataSourceLookupService;
import io.yak.ops.plugin.task.api.execution.TaskCursorBatch;
import io.yak.ops.plugin.task.api.execution.TaskCursorStore;
import io.yak.ops.plugin.task.api.execution.TaskExecutionIdentity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Resolves source-clock upper bounds and freezes one Task-owned incremental batch before execution. */
public final class IncrementalBatchPreparation {
    private final DataSourceLookupService resources;
    private final TaskCursorStore cursors;

    public IncrementalBatchPreparation(DataSourceLookupService resources, TaskCursorStore cursors) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.cursors = Objects.requireNonNull(cursors, "cursors");
    }

    public IncrementalRun prepare(PipelinePlan plan, PipelineOptions config, TaskExecutionIdentity identity)
            throws Exception {
        if (!config.bounded() || !config.steps().contains(ExecutionPhase.INCREMENTAL))
            throw new IllegalArgumentException("Expected bounded offline incremental task");
        List<TaskCursorBatch> proposals = new ArrayList<>();
        for (int index = 0; index < plan.tables().size(); index++) {
            TableBinding table = plan.tables().get(index);
            IncrementalConfig increment = config.tables().get(index).incremental();
            if (increment == null) throw new IllegalArgumentException("Missing per-table incremental configuration");
            String datasourceId = table.sourceParameters().get(EndpointOptions.DATASOURCE_ID);
            JdbcConnectionOptions conn = new JdbcConnectionOptions(resources.connectionOptions(
                    identity.workspaceId(), datasourceId, table.source().revision()));
            JdbcIncrementalRange.Mode mode =
                    JdbcIncrementalRange.Mode.valueOf(increment.mode().name());
            String upper = JdbcIncrementalUpperBound.read(
                    conn, table.source().table(), mode, increment.column(), increment.delaySeconds());
            String tableId = digest(
                    datasourceId,
                    table.source().table(),
                    table.sinkParameters().get(EndpointOptions.DATASOURCE_ID),
                    table.targetTable());
            String binding = sha(tableId
                    + '|'
                    + mode
                    + '|'
                    + increment.column()
                    + '|'
                    + increment.initialValue()
                    + '|'
                    + increment.delaySeconds()
                    + '|'
                    + increment.lookbackSeconds()
                    + '|'
                    + config.steps()
                    + '|'
                    + table.source().revision()
                    + '|'
                    + table.targetDatasource().revision()
                    + '|'
                    + table.transformRevision()
                    + readRevision(table));
            proposals.add(TaskCursorBatch.proposal(
                    tableId,
                    binding,
                    mode.name(),
                    increment.column(),
                    increment.initialValue(),
                    upper,
                    config.steps().contains(ExecutionPhase.FULL)));
        }
        List<TaskCursorBatch> frozen = cursors.freeze(identity, proposals);
        Map<String, TaskCursorBatch> byKey = new HashMap<String, TaskCursorBatch>();
        for (TaskCursorBatch row : frozen) byKey.put(row.tableKey(), row);
        if (byKey.size() != plan.tables().size())
            throw new IllegalStateException("Frozen incremental plan is incomplete");
        boolean initialLoad = frozen.getFirst().initialLoad();
        List<TableBinding> enriched = new ArrayList<>();
        for (int index = 0; index < plan.tables().size(); index++) {
            TableBinding table = plan.tables().get(index);
            TaskCursorBatch proposal = proposals.get(index);
            TaskCursorBatch batch = byKey.get(proposal.tableKey());
            if (batch == null || !batch.binding().equals(proposal.binding()) || batch.initialLoad() != initialLoad)
                throw new IllegalStateException("Frozen cursor identity or stage differs from current binding");
            if (initialLoad) {
                enriched.add(table);
                continue;
            }
            // An existing committed cursor must never continue into a newly created empty target.
            if (table.targetSchema() == null) {
                throw new IllegalStateException("Incremental target is missing; explicit initialization required");
            }
            IncrementalConfig increment = config.tables().get(index).incremental();
            String lower = batch.lower();
            if (increment.mode() == IncrementalConfig.Mode.TIMESTAMP && lower != null) {
                LocalDateTime extended = LocalDateTime.parse(lower).minusSeconds(increment.lookbackSeconds());
                if (increment.initialValue() != null
                        && !increment.initialValue().isBlank()
                        && extended.isBefore(LocalDateTime.parse(increment.initialValue()))) {
                    extended = LocalDateTime.parse(increment.initialValue());
                }
                lower = extended.toString();
            }
            JdbcIncrementalRange range = new JdbcIncrementalRange(
                    JdbcIncrementalRange.Mode.valueOf(batch.mode()), batch.column(), lower, batch.upper());
            Map<String, Object> opts = new LinkedHashMap<>(table.sourceOptions().getSourceMap());
            opts.put(JdbcSourceOptions.RANGE_MODE.key(), range.mode().name());
            opts.put(JdbcSourceOptions.RANGE_COLUMN.key(), range.column());
            if (range.lower() != null) opts.put(JdbcSourceOptions.RANGE_LOWER.key(), range.lower());
            opts.put(JdbcSourceOptions.RANGE_UPPER.key(), range.upper());
            enriched.add(table.withSourceOptions(ReadonlyConfig.fromMap(opts)));
        }
        return new IncrementalRun(plan.withTables(enriched), initialLoad);
    }

    private static String readRevision(TableBinding table) {
        Map<String, Object> selected = new java.util.TreeMap<>();
        for (String key : List.of("read.where", "read.split-key")) {
            Object value = table.sourceOptions().getSourceMap().get(key);
            if (value != null) selected.put(key, value);
        }
        return selected.isEmpty() ? "" : "|read:" + sha(selected.toString());
    }

    private static String digest(String sourceId, TableId source, String targetId, TableId target) {
        return sha(sourceId + '|' + source + '|' + targetId + '|' + target);
    }

    private static String sha(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    /** One frozen incremental batch and its actual initial-load decision. */
    public static final class IncrementalRun {
        private final PipelinePlan plan;
        private final boolean initialLoad;

        public IncrementalRun(PipelinePlan plan, boolean initialLoad) {
            this.plan = Objects.requireNonNull(plan, "plan");
            this.initialLoad = initialLoad;
        }

        public PipelinePlan plan() {
            return plan;
        }

        public boolean initialLoad() {
            return initialLoad;
        }

        /** Rebinds frozen source scan ranges after target DDL readback while preserving admission. */
        public PipelinePlan apply(PipelinePlan resolved) {
            if (resolved.tables().size() != plan.tables().size())
                throw new IllegalStateException("Table count changed");
            List<TableBinding> revised = new ArrayList<>();
            for (int index = 0; index < plan.tables().size(); index++) {
                TableBinding original = plan.tables().get(index);
                TableBinding latest = resolved.tables().get(index);
                if (!original.source().table().equals(latest.source().table()))
                    throw new IllegalStateException("Source table identity changed after preparation");
                revised.add(latest.withSourceOptions(original.sourceOptions()));
            }
            return resolved.withTables(revised);
        }
    }
}
