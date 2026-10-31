package io.yak.ops.connector.cdc.mysql.source.config;

import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.connector.jdbc.database.query.JdbcReadQuery;
import io.yak.ops.core.data.RowKind;
import java.io.Serializable;

/** Immutable table-specific snapshot and original Binlog mutation selection. */
public final class MySqlTableOptions implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final MySqlTableOptions DEFAULT = new MySqlTableOptions(false, null, true, true, true);

    private final boolean skipSnapshot;
    private final String snapshotWhere;
    private final boolean insert;
    private final boolean update;
    private final boolean delete;

    public MySqlTableOptions(
            boolean skipSnapshot, String snapshotWhere, boolean insert, boolean update, boolean delete) {
        this.skipSnapshot = skipSnapshot;
        this.snapshotWhere = JdbcReadQuery.validateWhere(snapshotWhere);
        if (!insert && !update && !delete) {
            throw new IllegalArgumentException("At least one CDC mutation must be selected");
        }
        if (skipSnapshot && this.snapshotWhere != null) {
            throw new IllegalArgumentException("A skipped snapshot cannot have a read predicate");
        }
        this.insert = insert;
        this.update = update;
        this.delete = delete;
    }

    public static MySqlTableOptions from(ReadonlyConfig config) {
        return new MySqlTableOptions(
                config.get(MySqlSourceOptions.SNAPSHOT_SKIP),
                config.get(MySqlSourceOptions.SNAPSHOT_WHERE),
                config.get(MySqlSourceOptions.DML_INSERT),
                config.get(MySqlSourceOptions.DML_UPDATE),
                config.get(MySqlSourceOptions.DML_DELETE));
    }

    public boolean skipSnapshot() {
        return skipSnapshot;
    }

    public String snapshotWhere() {
        return snapshotWhere;
    }

    /** UPDATE images are selected together, before transforms can change their row kind. */
    public boolean accepts(RowKind kind) {
        return switch (kind) {
            case INSERT -> insert;
            case UPDATE_BEFORE, UPDATE_AFTER -> update;
            case DELETE -> delete;
        };
    }
}
