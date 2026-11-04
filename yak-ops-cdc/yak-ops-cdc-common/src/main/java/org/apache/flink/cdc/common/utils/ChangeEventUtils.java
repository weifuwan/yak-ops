package org.apache.flink.cdc.common.utils;

import org.apache.flink.cdc.common.annotation.VisibleForTesting;
import org.apache.flink.cdc.common.event.DataChangeEvent;
import org.apache.flink.cdc.common.event.SchemaChangeEventType;
import org.apache.flink.cdc.common.event.SchemaChangeEventTypeFamily;
import org.apache.flink.cdc.common.event.TableId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Utilities for handling {@link org.apache.flink.cdc.common.event.ChangeEvent}s. */
public class ChangeEventUtils {
    public static DataChangeEvent recreateDataChangeEvent(
            DataChangeEvent dataChangeEvent, TableId tableId) {
        switch (dataChangeEvent.op()) {
            case INSERT:
                return DataChangeEvent.insertEvent(
                        tableId, dataChangeEvent.after(), dataChangeEvent.meta());
            case UPDATE:
                return DataChangeEvent.updateEvent(
                        tableId,
                        dataChangeEvent.before(),
                        dataChangeEvent.after(),
                        dataChangeEvent.meta());
            case REPLACE:
                return DataChangeEvent.replaceEvent(
                        tableId, dataChangeEvent.after(), dataChangeEvent.meta());
            case DELETE:
                return DataChangeEvent.deleteEvent(
                        tableId, dataChangeEvent.before(), dataChangeEvent.meta());
            default:
                throw new UnsupportedOperationException(
                        String.format(
                                "Unsupported operation type \"%s\" in data change event",
                                dataChangeEvent.op()));
        }
    }

    public static Set<SchemaChangeEventType> resolveSchemaEvolutionOptions(
            List<String> includedSchemaEvolutionTypes, List<String> excludedSchemaEvolutionTypes) {
        List<SchemaChangeEventType> resultTypes = new ArrayList<>();

        for (String includeTag : includedSchemaEvolutionTypes) {
            resultTypes.addAll(resolveSchemaEvolutionTag(includeTag));
        }

        for (String excludeTag : excludedSchemaEvolutionTypes) {
            resultTypes.removeAll(resolveSchemaEvolutionTag(excludeTag));
        }

        return new HashSet<>(resultTypes);
    }

    @VisibleForTesting
    public static List<SchemaChangeEventType> resolveSchemaEvolutionTag(String tag) {
        List<SchemaChangeEventType> types =
                new ArrayList<>(Arrays.asList(SchemaChangeEventTypeFamily.ofTag(tag)));
        if (types.isEmpty()) {
            // It's a specified tag
            SchemaChangeEventType type = SchemaChangeEventType.ofTag(tag);
            if (type != null) {
                types.add(type);
            }
        }
        return types;
    }
}
