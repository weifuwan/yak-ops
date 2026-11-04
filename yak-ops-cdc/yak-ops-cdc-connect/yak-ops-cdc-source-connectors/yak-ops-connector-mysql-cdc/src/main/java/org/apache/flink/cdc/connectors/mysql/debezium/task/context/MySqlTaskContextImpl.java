package org.apache.flink.cdc.connectors.mysql.debezium.task.context;

import com.github.shyiko.mysql.binlog.BinaryLogClient;
import io.debezium.connector.mysql.MySqlConnectorConfig;
import io.debezium.connector.mysql.MySqlDatabaseSchema;
import io.debezium.connector.mysql.MySqlTaskContext;

/** A subclass implementation of {@link MySqlTaskContext} which reuses one BinaryLogClient. */
public class MySqlTaskContextImpl extends MySqlTaskContext {

    private final BinaryLogClient reusedBinaryLogClient;

    public MySqlTaskContextImpl(
            MySqlConnectorConfig config,
            MySqlDatabaseSchema schema,
            BinaryLogClient reusedBinaryLogClient) {
        super(config, schema);
        this.reusedBinaryLogClient = reusedBinaryLogClient;
    }

    @Override
    public BinaryLogClient getBinaryLogClient() {
        return reusedBinaryLogClient;
    }
}
