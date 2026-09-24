package io.yak.ops.business.datasource.dao.model;

import io.yak.ops.core.execution.sql.SqlStatementType;
import lombok.Data;

/** Statement semantic distribution aggregate. */
@Data
public class SqlStatementTypeCountRow {
    private SqlStatementType statementType;
    private long count;
}
