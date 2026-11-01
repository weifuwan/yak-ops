package io.yak.ops.core.configuration;

import io.yak.ops.core.event.SchemaChangeEventType;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, fully specified per-event DDL decisions for the continuous CDC pipeline.
 *
 * <p>Do not treat the presence of an event identifier as evidence that the source can classify it,
 * or that the target can execute it. A connector must independently validate its capabilities.
 * The compact encoding is internal to a Job configuration; the public Task JSON uses named fields.
 */
public final class DdlStrategies {
    public enum Action {
        NORMAL,
        IGNORE,
        ERROR
    }

    public enum Event {
        CREATE_TABLE("createTable", "create-table"),
        DROP_TABLE("dropTable", "drop-table"),
        ADD_COLUMN("addColumn", "add-column"),
        DROP_COLUMN("dropColumn", "drop-column"),
        RENAME_TABLE("renameTable", "rename-table"),
        RENAME_COLUMN("renameColumn", "rename-column"),
        MODIFY_COLUMN_TYPE("modifyColumnType", "modify-column-type"),
        TRUNCATE_TABLE("truncateTable", "truncate-table");

        private final String jsonKey;
        private final String yamlKey;

        Event(String jsonKey, String yamlKey) {
            this.jsonKey = jsonKey;
            this.yamlKey = yamlKey;
        }

        public String jsonKey() {
            return jsonKey;
        }

        public String yamlKey() {
            return yamlKey;
        }

        public static Event ofJsonKey(String key) {
            for (Event event : values()) if (event.jsonKey.equals(key)) return event;
            throw new IllegalArgumentException("Unknown DDL event key: " + key);
        }

        public static Event ofYamlKey(String key) {
            for (Event event : values()) if (event.yamlKey.equals(key)) return event;
            throw new IllegalArgumentException("Unknown DDL event key: " + key);
        }

        public static Event ofSchemaType(SchemaChangeEventType type) {
            return switch (Objects.requireNonNull(type, "type")) {
                case CREATE_TABLE -> CREATE_TABLE;
                case DROP_TABLE -> DROP_TABLE;
                case ADD_COLUMN -> ADD_COLUMN;
                case DROP_COLUMN -> DROP_COLUMN;
                case RENAME_COLUMN -> RENAME_COLUMN;
                case ALTER_COLUMN_TYPE -> MODIFY_COLUMN_TYPE;
                case TRUNCATE_TABLE -> TRUNCATE_TABLE;
                case ALTER_TABLE_COMMENT ->
                    throw new UnsupportedOperationException("ALTER TABLE COMMENT has no configured DDL strategy");
            };
        }
    }

    private final Map<Event, Action> actions;

    public DdlStrategies(Map<Event, Action> configured) {
        Objects.requireNonNull(configured, "configured");
        if (configured.size() != Event.values().length) {
            throw new IllegalArgumentException("All eight DDL strategies are required");
        }
        EnumMap<Event, Action> copy = new EnumMap<>(Event.class);
        for (Event event : Event.values()) {
            Action action = Objects.requireNonNull(configured.get(event), "DDL action for " + event.jsonKey());
            if (action == Action.NORMAL
                    && List.of(Event.DROP_TABLE, Event.RENAME_TABLE, Event.RENAME_COLUMN, Event.TRUNCATE_TABLE)
                            .contains(event)) {
                throw new UnsupportedOperationException(
                        "DDL NORMAL is not available until the connector has a complete runtime implementation: "
                                + event.jsonKey());
            }
            if (action == Action.IGNORE
                    && List.of(Event.DROP_TABLE, Event.RENAME_TABLE, Event.RENAME_COLUMN, Event.TRUNCATE_TABLE)
                            .contains(event)) {
                throw new UnsupportedOperationException(
                        "DDL IGNORE is not supported by the current source identity/recovery protocol: "
                                + event.jsonKey());
            }
            copy.put(event, action);
        }
        actions = Collections.unmodifiableMap(copy);
    }

    public Action action(Event event) {
        return actions.get(Objects.requireNonNull(event, "event"));
    }

    public boolean dropsColumns() {
        return action(Event.DROP_COLUMN) == Action.NORMAL;
    }

    public Map<String, String> toJsonValues() {
        Map<String, String> result = new LinkedHashMap<>();
        for (Event event : Event.values())
            result.put(event.jsonKey(), action(event).name());
        return Collections.unmodifiableMap(result);
    }

    /** Strict internal representation with a stable order for Task identity and diagnostics. */
    public String encode() {
        StringBuilder result = new StringBuilder();
        for (Event event : Event.values()) {
            if (!result.isEmpty()) result.append(',');
            result.append(event.yamlKey()).append('=').append(action(event));
        }
        return result.toString();
    }

    public static DdlStrategies parse(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("DDL strategy encoding is missing");
        }
        EnumMap<Event, Action> result = new EnumMap<>(Event.class);
        for (String item : encoded.split(",", -1)) {
            int split = item.indexOf('=');
            if (split < 1 || split != item.lastIndexOf('=') || split == item.length() - 1) {
                throw new IllegalArgumentException("Malformed DDL strategy");
            }
            Event event = Event.ofYamlKey(item.substring(0, split));
            Action action;
            try {
                action = Action.valueOf(item.substring(split + 1).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("Unsupported DDL action for " + event.yamlKey(), invalid);
            }
            if (result.putIfAbsent(event, action) != null) {
                throw new IllegalArgumentException("Duplicate DDL strategy: " + event.yamlKey());
            }
        }
        return new DdlStrategies(result);
    }
}
