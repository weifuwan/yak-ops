package io.yak.ops.plugin.task.datasync.config;

import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.cdc.mysql.source.MySqlCdcSourceOptions;
import io.yak.ops.plugin.task.datasync.solution.SolutionType;
import io.yak.ops.plugin.task.datasync.solution.Step;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validates one Source table's read intent and constructs its Connector-scoped inputs. */
public record SourceConfig(ReadonlyConfig options, boolean keyless, IncrementalConfig incremental) {
    public static final Option<String> ID = Options.key("id").stringType().noDefaultValue();
    private static final Option<Boolean> KEYLESS =
            Options.key("keyless").booleanType().defaultValue(false);
    private static final Option<Map<String, Object>> SNAPSHOT =
            Options.key("snapshot").mapObjectType().noDefaultValue();
    private static final Option<String> CHUNK_KEY =
            Options.key("chunkKeyColumn").stringType().noDefaultValue();
    private static final Option<Map<String, Object>> INCREMENTAL =
            Options.key("incremental").mapObjectType().noDefaultValue();

    public static final OptionRule TABLE_RULE = OptionRule.builder()
            .required(ID, Conditions.notBlank(ID))
            .required(JobOptions.TABLE, Conditions.notBlank(JobOptions.TABLE))
            .optional(JobOptions.DATABASE, JobOptions.SCHEMA, KEYLESS, SNAPSHOT, INCREMENTAL)
            .build();

    private static final OptionRule SNAPSHOT_RULE =
            OptionRule.builder().required(CHUNK_KEY).build();

    public static SourceConfig from(
            ReadonlyConfig table,
            String datasourceId,
            SolutionType type,
            List<Step> steps,
            boolean schemaChangesEnabled) {
        validate(table, TABLE_RULE, "source.tables");
        boolean keyless = table.get(KEYLESS);
        Map<String, Object> snapshot = table.get(SNAPSHOT);
        if (keyless) {
            if (type != SolutionType.SINGLE_TABLE_REALTIME
                    || !steps.containsAll(List.of(Step.FULL, Step.INCREMENTAL))
                    || schemaChangesEnabled
                    || snapshot == null) {
                throw new IllegalArgumentException("Keyless CDC requires a supported realtime snapshot");
            }
        } else if (snapshot != null) {
            throw new IllegalArgumentException("Snapshot Chunk Key requires explicit keyless CDC");
        }
        String chunkKey = null;
        if (snapshot != null) {
            ReadonlyConfig settings = ReadonlyConfig.fromMap(snapshot);
            validate(settings, SNAPSHOT_RULE, "source.tables.snapshot");
            chunkKey = settings.get(CHUNK_KEY);
            if (chunkKey == null || chunkKey.isBlank() || chunkKey.length() > 128) {
                throw new IllegalArgumentException("Snapshot Chunk Key must identify a source column");
            }
        }
        boolean offlineIncremental = type.supportsScheduling() && steps.contains(Step.INCREMENTAL);
        Map<String, Object> options = table.get(INCREMENTAL);
        if (offlineIncremental && options == null) {
            throw new IllegalArgumentException("Offline incremental Source settings are required");
        }
        if (!offlineIncremental && options != null) {
            throw new IllegalArgumentException("Incremental settings require an offline incremental step");
        }
        IncrementalConfig incremental = options == null ? null : IncrementalConfig.from(options);
        Map<String, Object> physical = new LinkedHashMap<>(table.getSourceMap());
        for (Option<?> field : List.of(ID, KEYLESS, SNAPSHOT, INCREMENTAL)) {
            physical.remove(field.key());
        }
        physical.put(JobOptions.DATASOURCE_ID.key(), datasourceId);
        physical.put(JobOptions.TYPE.key(), type.sourceIdentifier());
        if (keyless) {
            physical.put(MySqlCdcSourceOptions.KEYLESS_ENABLED.key(), true);
            physical.put(MySqlCdcSourceOptions.CHUNK_KEY_COLUMN.key(), chunkKey);
        }
        return new SourceConfig(ReadonlyConfig.fromMap(physical), keyless, incremental);
    }

    private static void validate(ReadonlyConfig settings, OptionRule rule, String component) {
        ConfigValidator.of(settings).validate(rule);
        ConfigValidator.validateUnknownKeys(settings, rule, component);
    }
}
