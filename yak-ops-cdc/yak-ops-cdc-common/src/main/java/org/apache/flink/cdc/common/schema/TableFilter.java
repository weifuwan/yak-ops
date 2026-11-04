package org.apache.flink.cdc.common.schema;

import org.apache.flink.cdc.common.event.TableId;

import java.util.function.Predicate;

/** A filter for tables. */
@FunctionalInterface
public interface TableFilter {

    /** Determines whether the given table should be included. */
    boolean isIncluded(TableId tableId);

    /** Creates a {@link TableFilter} from the given predicate. */
    static TableFilter fromPredicate(Predicate<TableId> predicate) {
        return predicate::test;
    }

    /** Creates a {@link TableFilter} that includes all tables. */
    static TableFilter includeAll() {
        return t -> true;
    }
}
