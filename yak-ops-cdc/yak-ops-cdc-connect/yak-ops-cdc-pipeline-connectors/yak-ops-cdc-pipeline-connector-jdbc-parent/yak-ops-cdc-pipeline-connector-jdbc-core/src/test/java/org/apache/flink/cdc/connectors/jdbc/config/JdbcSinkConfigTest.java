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
