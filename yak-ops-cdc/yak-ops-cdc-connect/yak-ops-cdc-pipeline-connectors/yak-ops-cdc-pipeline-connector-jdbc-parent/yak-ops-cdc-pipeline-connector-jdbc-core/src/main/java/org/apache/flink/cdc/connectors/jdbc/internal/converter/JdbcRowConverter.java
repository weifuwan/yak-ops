package org.apache.flink.cdc.connectors.jdbc.internal.converter;

import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.connectors.jdbc.internal.data.JdbcRowData;
import org.apache.flink.connector.jdbc.JdbcStatementBuilder;

import java.io.Serializable;
import java.util.List;

/** Converts schema-ordered CDC rows to JDBC statement parameters. */
public interface JdbcRowConverter extends Serializable {
    JdbcStatementBuilder<JdbcRowData> createStatementBuilder(
            Schema tableSchema, List<String> statementColumns, List<String> rowColumns);

    default JdbcStatementBuilder<JdbcRowData> createStatementBuilder(
            List<String> statementColumns, List<String> rowColumns) {
        return createStatementBuilder(null, statementColumns, rowColumns);
    }
}
