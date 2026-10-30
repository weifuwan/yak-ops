package io.yak.ops.sync.runtime.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.yak.ops.configuration.Option;
import io.yak.ops.configuration.Options;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.configuration.rule.Conditions;
import io.yak.ops.configuration.rule.ConfigValidator;
import io.yak.ops.configuration.rule.OptionRule;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceOptions;
import io.yak.ops.connector.cdc.mysql.table.StartupMode;
import io.yak.ops.connector.cdc.mysql.table.StartupOptions;
import io.yak.ops.sync.runtime.solution.SolutionType;
import io.yak.ops.sync.runtime.solution.Step;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validates one Source table's read intent and constructs its Connector-scoped inputs. */
public final class SourceConfig {
    private final ReadonlyConfig options;
    private final IncrementalConfig incremental;

    @JsonCreator
    public SourceConfig(
            @JsonProperty("options") ReadonlyConfig options,
            @JsonProperty("incremental") IncrementalConfig incremental) {
        this.options = options;
        this.incremental = incremental;
    }

    @JsonProperty("options")
    public ReadonlyConfig options() {
        return options;
    }

    @JsonProperty("incremental")
    public IncrementalConfig incremental() {
        return incremental;
    }

    @Override
    public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof SourceConfig other)) return false;
        return java.util.Objects.equals(options, other.options)
                && java.util.Objects.equals(incremental, other.incremental);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(options, incremental);
    }

    public static final Option<String> ID = Options.key("id").stringType().noDefaultValue();
    private static final Option<Map<String, Object>> SNAPSHOT =
            Options.key("snapshot").mapObjectType().noDefaultValue();
    private static final Option<String> CHUNK_KEY =
            Options.key("chunkKeyColumn").stringType().noDefaultValue();
    private static final Option<Map<String, Object>> INCREMENTAL =
            Options.key("incremental").mapObjectType().noDefaultValue();

    public static final OptionRule TABLE_RULE = OptionRule.builder()
            .required(ID, Conditions.notBlank(ID))
            .required(JobOptions.TABLE, Conditions.notBlank(JobOptions.TABLE))
            .optional(JobOptions.DATABASE, JobOptions.SCHEMA, SNAPSHOT, INCREMENTAL)
            .build();

    private static final OptionRule SNAPSHOT_RULE =
            OptionRule.builder().required(CHUNK_KEY).build();

    public static SourceConfig from(
            ReadonlyConfig table,
            String datasourceId,
            SolutionType type,
            List<Step> steps,
            StartupOptions startupOptions) {
        validate(table, TABLE_RULE, "source.tables");
        Map<String, Object> snapshot = table.get(SNAPSHOT);
        if (snapshot != null && (type.supportsScheduling() || !steps.contains(Step.FULL))) {
            throw new IllegalArgumentException("Snapshot Chunk Key requires a realtime FULL snapshot");
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
        for (Option<?> field : List.of(ID, SNAPSHOT, INCREMENTAL)) {
            physical.remove(field.key());
        }
        physical.put(JobOptions.DATASOURCE_ID.key(), datasourceId);
        physical.put(JobOptions.TYPE.key(), type.sourceIdentifier());
        if (chunkKey != null)
            physical.put(MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN.key(), chunkKey);
        if (startupOptions != null) {
            physical.put(MySqlSourceOptions.SCAN_STARTUP_MODE.key(), startupOptions.asModeString());
            if (startupOptions.startupMode() == StartupMode.SPECIFIC_OFFSETS) {
                io.yak.ops.connector.cdc.mysql.source.offset.BinlogOffset offset = startupOptions.binlogOffset;
                if (offset.getFilename() != null && !offset.getFilename().isBlank()) {
                    physical.put(MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_FILE.key(), offset.getFilename());
                    physical.put(MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_POS.key(), offset.getPosition());
                }
                if (offset.getGtidSet() != null && !offset.getGtidSet().isBlank()) {
                    physical.put(MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_GTID_SET.key(), offset.getGtidSet());
                }
                if (offset.getRestartSkipEvents() != 0) {
                    physical.put(
                            MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_SKIP_EVENTS.key(),
                            offset.getRestartSkipEvents());
                }
                if (offset.getRestartSkipRows() != 0) {
                    physical.put(
                            MySqlSourceOptions.SCAN_STARTUP_SPECIFIC_OFFSET_SKIP_ROWS.key(),
                            offset.getRestartSkipRows());
                }
            } else if (startupOptions.startupMode() == StartupMode.TIMESTAMP) {
                physical.put(
                        MySqlSourceOptions.SCAN_STARTUP_TIMESTAMP_MILLIS.key(),
                        Math.multiplyExact(startupOptions.binlogOffset.getTimestampSec(), 1000L));
            }
        }
        return new SourceConfig(ReadonlyConfig.fromMap(physical), incremental);
    }

    private static void validate(ReadonlyConfig settings, OptionRule rule, String component) {
        ConfigValidator.of(settings).validate(rule);
        ConfigValidator.validateUnknownKeys(settings, rule, component);
    }
}
