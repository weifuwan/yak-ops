package org.apache.flink.cdc.connectors.jdbc.mysql.converter;

import org.apache.flink.cdc.common.types.DataTypeRoot;
import org.apache.flink.cdc.connectors.jdbc.internal.converter.DefaultJdbcRowConverter;

import java.sql.Types;

/** MySQL drivers expose TIMESTAMP_LTZ as JDBC TIMESTAMP, not TIMESTAMP_WITH_TIMEZONE. */
public class MySqlRowConverter extends DefaultJdbcRowConverter {
    @Override
    protected int jdbcType(DataTypeRoot root) {
        if (root == DataTypeRoot.TIMESTAMP_WITH_LOCAL_TIME_ZONE) {
            return Types.TIMESTAMP;
        }
        return super.jdbcType(root);
    }
}
