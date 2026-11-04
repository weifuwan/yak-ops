/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.cdc.connectors.jdbc.internal;

import org.apache.flink.cdc.common.event.TableId;
import org.apache.flink.cdc.common.schema.Schema;
import org.apache.flink.cdc.common.types.DataTypes;
import org.apache.flink.cdc.connectors.jdbc.internal.converter.DefaultJdbcRowConverter;
import org.apache.flink.cdc.connectors.jdbc.internal.dialect.JdbcDialect;
import org.apache.flink.connector.jdbc.JdbcExecutionOptions;
import org.apache.flink.connector.jdbc.datasource.connections.JdbcConnectionProvider;
import org.apache.flink.connector.jdbc.internal.JdbcOutputSerializer;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies Flink JdbcOutputFormat resource cleanup when statement initialization fails. */
class JdbcOutputFormatBuilderTest {

    @Test
    void closesEstablishedConnectionWhenStatementPreparationFails() throws Exception {
        FailingConnectionProvider provider = new FailingConnectionProvider(false);
        IOException error = assertThrows(IOException.class, () -> builder(provider).build(
                TableId.tableId("db", "orders"), schema()));
        assertTrue(error.getMessage().contains("unable to open JDBC writer"));
        assertEquals(1, provider.closeCalls);
    }

    @Test
    void closesProviderEvenWhenConnectionAcquisitionFails() throws Exception {
        FailingConnectionProvider provider = new FailingConnectionProvider(true);
        assertThrows(IOException.class, () -> builder(provider).build(
                TableId.tableId("db", "orders"), schema()));
        assertEquals(1, provider.closeCalls);
    }

    private static Schema schema() {
        return Schema.newBuilder()
                .physicalColumn("id", DataTypes.BIGINT().notNull())
                .primaryKey("id")
                .build();
    }

    private static JdbcOutputFormatBuilder builder(JdbcConnectionProvider provider) {
        JdbcDialect dialect = (JdbcDialect) Proxy.newProxyInstance(
                JdbcOutputFormatBuilderTest.class.getClassLoader(),
                new Class<?>[] {JdbcDialect.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUpsertStatement")) {
                        return "INSERT INTO orders (id) VALUES (?)";
                    }
                    if (method.getName().equals("getDeleteStatement")) {
                        return "DELETE FROM orders WHERE id = ?";
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
        return new JdbcOutputFormatBuilder(
                JdbcExecutionOptions.defaults(),
                provider,
                JdbcOutputSerializer.<Object>of(null),
                dialect,
                new DefaultJdbcRowConverter());
    }

    private static final class FailingConnectionProvider implements JdbcConnectionProvider {
        private final boolean failConnection;
        private final Connection connection;
        private int closeCalls;

        FailingConnectionProvider(boolean failConnection) {
            this.failConnection = failConnection;
            this.connection = (Connection) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement")) {
                            throw new SQLException("Cannot prepare statement");
                        }
                        if (method.getName().equals("close")) {
                            return null;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        @Override
        public Connection getConnection() {
            return connection;
        }

        @Override
        public Properties getProperties() {
            return new Properties();
        }

        @Override
        public boolean isConnectionValid() {
            return !failConnection;
        }

        @Override
        public Connection getOrEstablishConnection() throws SQLException {
            if (failConnection) {
                throw new SQLException("Connection refused");
            }
            return connection;
        }

        @Override
        public void closeConnection() {
            closeCalls++;
        }

        @Override
        public Connection reestablishConnection() throws SQLException {
            return getOrEstablishConnection();
        }
    }
}
