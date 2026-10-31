package io.yak.ops.composer.definition;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlSourceOptions;
import io.yak.ops.connector.cdc.mysql.source.config.MySqlTableOptions;
import io.yak.ops.core.data.TableId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable matching and naming rules; discovered identities belong to runtime checkpoints. */
public final class DynamicTableSelection {
    private final String databasePattern;
    private final String tablePattern;
    private final String database;
    private final String schema;
    private final String table;
    private final Map<String, Object> sourceDefaults;
    private final Map<String, Object> sinkDefaults;

    private DynamicTableSelection(Map<String, String> from, Map<String, String> to) {
        databasePattern = required(from, "yak.selection.database-pattern");
        tablePattern = required(from, "yak.selection.table-pattern");
        if (databasePattern.length() > 256 || tablePattern.length() > 256) {
            throw new IllegalArgumentException("Dynamic table patterns must not exceed 256 characters");
        }
        Pattern.compile(databasePattern);
        Pattern.compile(tablePattern);
        database = template(to.get("yak.mapping.database"), false);
        schema = template(to.get("yak.mapping.schema"), false);
        table = template(required(to, "yak.mapping.table"), true);
        Map<String, Object> read = new LinkedHashMap<>();
        for (String name : List.of("snapshot.skip", "snapshot.where", "dml.insert", "dml.update", "dml.delete")) {
            if (from.containsKey("yak.selection." + name)) read.put(name, from.get("yak.selection." + name));
        }
        if (from.containsKey("yak.selection.snapshot.chunk-key-column")) {
            read.put(
                    MySqlSourceOptions.SCAN_INCREMENTAL_SNAPSHOT_CHUNK_KEY_COLUMN.key(),
                    from.get("yak.selection.snapshot.chunk-key-column"));
        }
        MySqlTableOptions.from(ReadonlyConfig.fromMap(read));
        sourceDefaults = Map.copyOf(read);
        Map<String, Object> write = new LinkedHashMap<>();
        String mode = to.getOrDefault("yak.mapping.write.mode", "UPSERT");
        if (!mode.equals("UPSERT") || to.containsKey("yak.mapping.write.transaction.mode")) {
            throw new IllegalArgumentException("Dynamic tables require UPSERT without checkpoint XA");
        }
        write.put("write.mode", mode);
        if (to.containsKey("yak.mapping.write.primary-keys")) {
            throw new IllegalArgumentException("Dynamic table defaults use each source table's physical primary key");
        }
        if (to.containsKey("yak.mapping.write.batch-size")) {
            int batch = Integer.parseInt(to.get("yak.mapping.write.batch-size"));
            if (batch < 1 || batch > 10000) throw new IllegalArgumentException("Invalid dynamic write batch size");
            write.put("write.batch-size", batch);
        }
        sinkDefaults = Map.copyOf(write);
    }

    public static DynamicTableSelection from(PipelineDef definition) {
        Map<String, String> from = definition.getSource().getConfig().toMap();
        Map<String, String> to = definition.getSink().getConfig().toMap();
        String mode = from.get("yak.selection.mode");
        if (mode == null) {
            if (from.keySet().stream().anyMatch(key -> key.startsWith("yak.selection."))
                    || to.keySet().stream().anyMatch(key -> key.startsWith("yak.mapping."))) {
                throw new IllegalArgumentException("Dynamic options require REGEX table selection");
            }
            return null;
        }
        if (!mode.equals("REGEX") || !definition.getSource().getType().equals("mysql")) {
            throw new IllegalArgumentException("REGEX table discovery requires MySQL CDC");
        }
        return new DynamicTableSelection(from, to);
    }

    public boolean matches(TableId source) {
        return source.catalog() != null
                && source.schema() == null
                && Pattern.matches(databasePattern, source.catalog())
                && Pattern.matches(tablePattern, source.table());
    }

    /** Resolves only literal fragments and the three supported source-identifier placeholders. */
    public TableId target(TableId source) {
        if (!matches(source)) throw new IllegalArgumentException("Table does not match the published selection");
        return new TableId(expand(database, source), expand(schema, source), expand(table, source));
    }

    public Map<String, Object> sourceDefaults() {
        return sourceDefaults;
    }

    public Map<String, Object> sinkDefaults() {
        return sinkDefaults;
    }

    public String databasePattern() {
        return databasePattern;
    }

    public String tablePattern() {
        return tablePattern;
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing dynamic selection option");
        return value;
    }

    private static String template(String value, boolean required) {
        if (value == null) return null;
        String remainder =
                value.replace("${database}", "a").replace("${schema}", "a").replace("${table}", "a");
        if (!remainder.matches("[A-Za-z0-9_$]+") || required && value.isBlank()) {
            throw new IllegalArgumentException("Unsupported target identifier template");
        }
        return value;
    }

    private static String expand(String template, TableId source) {
        if (template == null) return null;
        String value = template.replace("${database}", Objects.toString(source.catalog(), ""))
                .replace("${schema}", Objects.toString(source.schema(), ""))
                .replace("${table}", source.table());
        if (!value.matches("[A-Za-z0-9_$]+"))
            throw new IllegalArgumentException("Target template resolves to an invalid identifier");
        return value;
    }
}
