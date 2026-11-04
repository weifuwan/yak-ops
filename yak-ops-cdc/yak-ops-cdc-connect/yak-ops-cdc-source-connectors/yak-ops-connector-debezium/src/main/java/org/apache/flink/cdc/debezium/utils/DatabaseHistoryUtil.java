package org.apache.flink.cdc.debezium.utils;

import org.apache.flink.cdc.debezium.DebeziumSourceFunction;
import org.apache.flink.cdc.debezium.internal.SchemaRecord;

import io.debezium.relational.history.DatabaseHistory;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Util to safely visit schema history between {@link DatabaseHistory} and {@link
 * DebeziumSourceFunction}.
 */
public class DatabaseHistoryUtil {

    private DatabaseHistoryUtil() {
        // do nothing
    }

    /**
     * Structure to maintain the current schema history. The content in {@link SchemaRecord} is up
     * to the implementation of the {@link DatabaseHistory}.
     */
    private static final Map<String, Collection<SchemaRecord>> HISTORY = new HashMap<>();

    /**
     * The schema history will be clean up once {@link DatabaseHistory#stop()}, the checkpoint
     * should fail when this happens.
     */
    private static final Map<String, Boolean> HISTORY_CLEANUP_STATUS = new HashMap<>();

    /** Registers history of schema safely. */
    public static void registerHistory(String engineName, Collection<SchemaRecord> engineHistory) {
        synchronized (HISTORY) {
            HISTORY.put(engineName, engineHistory);
            HISTORY_CLEANUP_STATUS.put(engineName, false);
        }
    }

    /** Remove history of schema safely. */
    public static void removeHistory(String engineName) {
        synchronized (HISTORY) {
            HISTORY_CLEANUP_STATUS.put(engineName, true);
            HISTORY.remove(engineName);
        }
    }

    /**
     * Retrieves history of schema safely, this method firstly checks the history status of specific
     * engine, and then return the history of schema if the history exists(didn't clean up yet).
     * Returns null when the history of schema has been clean up.
     */
    public static Collection<SchemaRecord> retrieveHistory(String engineName) {
        synchronized (HISTORY) {
            if (Boolean.TRUE.equals(HISTORY_CLEANUP_STATUS.get(engineName))) {
                throw new IllegalStateException(
                        String.format(
                                "Retrieve schema history failed, the schema records for engine %s has been removed,"
                                        + " this might because the debezium engine has been shutdown due to other errors.",
                                engineName));
            } else {
                return HISTORY.getOrDefault(engineName, Collections.emptyList());
            }
        }
    }
}
