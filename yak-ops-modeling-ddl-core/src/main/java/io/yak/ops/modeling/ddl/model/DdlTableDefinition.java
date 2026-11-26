package io.yak.ops.modeling.ddl.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Database-neutral physical table definition. */
public class DdlTableDefinition {

    private final String database;
    private final String tableName;
    private final String comment;
    private final List<DdlColumnDefinition> columns;

    public DdlTableDefinition(
            String database, String tableName, String comment, List<DdlColumnDefinition> columns) {
        this.database = database;
        this.tableName = tableName;
        this.comment = comment;
        this.columns = columns == null
                ? Collections.<DdlColumnDefinition>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(columns));
    }

    public String getDatabase() { return database; }
    public String getTableName() { return tableName; }
    public String getComment() { return comment; }
    public List<DdlColumnDefinition> getColumns() { return columns; }
}
