package org.apache.flink.cdc.connectors.jdbc.sink.v2;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;

import java.io.Serializable;

/** JdbcRowData represents extracting a row of data from a table in the database. */
public interface JdbcRowData extends Serializable {
    TableId getTableId();

    Schema getSchema();

    RowKind getRowKind();

    /** Values in schema column order, converted to JDBC-compatible Java types. */
    Object[] getRows();

    boolean hasPrimaryKey();
}
