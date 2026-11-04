package org.apache.flink.cdc.connectors.jdbc.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Resource bound configuration of the JDBC DML writer. */
class JdbcSinkConfigTest {

    @Test
    void defaultsToBoundedTableWriterConnections() {
        JdbcSinkConfig config =
                new JdbcSinkConfig.Builder<>().connUrl("jdbc:mysql://localhost:3306").build();
        assertEquals(32, config.getWriteMaxOpenTables());
    }

    @Test
    void validatesExplicitTableWriterBound() {
        JdbcSinkConfig config =
                new JdbcSinkConfig.Builder<>()
                        .connUrl("jdbc:mysql://localhost:3306")
                        .writeMaxOpenTables(3)
                        .build();
        assertEquals(3, config.getWriteMaxOpenTables());
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcSinkConfig.Builder<>().writeMaxOpenTables(0));
    }
}
