package org.apache.flink.cdc.connectors.mysql.schema;

import org.apache.flink.cdc.connectors.mysql.source.utils.StatementUtils;
import org.apache.flink.util.CollectionUtil;

import io.debezium.relational.TableId;

import java.util.List;
import java.util.stream.Collectors;

/** used to generate table definition in ddl with "desc table". */
public class MySqlTableDefinition {
    TableId tableId;
    List<MySqlFieldDefinition> fieldDefinitions;
    List<String> primaryKeys;

    public MySqlTableDefinition(
            TableId tableId,
            List<MySqlFieldDefinition> fieldDefinitions,
            List<String> primaryKeys) {
        this.tableId = tableId;
        this.fieldDefinitions = fieldDefinitions;
        this.primaryKeys = primaryKeys;
    }

    public String toDdl() {
        return String.format(
                "CREATE TABLE %s (\n\t %s %s );",
                StatementUtils.quote(tableId), fieldDefinitions(), pkDefinition());
    }

    private String fieldDefinitions() {
        return fieldDefinitions.stream()
                .map(MySqlFieldDefinition::toDdl)
                .collect(Collectors.joining(", \n\t"));
    }

    private String pkDefinition() {
        StringBuilder pkDefinition = new StringBuilder();
        if (!CollectionUtil.isNullOrEmpty(primaryKeys)) {
            pkDefinition.append(",");
            pkDefinition.append(
                    String.format(
                            "PRIMARY KEY ( %s )",
                            primaryKeys.stream()
                                    .map(StatementUtils::quote)
                                    .collect(Collectors.joining(","))));
        }
        return pkDefinition.toString();
    }
}
