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

package org.apache.flink.cdc.connectors.jdbc.mysql.factory;

import org.apache.flink.cdc.common.utils.Preconditions;
import org.apache.flink.cdc.connectors.jdbc.catalog.JdbcCatalog;
import org.apache.flink.cdc.connectors.jdbc.config.JdbcSinkConfig;
import org.apache.flink.cdc.connectors.jdbc.internal.dialect.JdbcDialect;
import org.apache.flink.cdc.connectors.jdbc.internal.dialect.JdbcDialectFactory;
import org.apache.flink.cdc.connectors.jdbc.mysql.catalog.MySqlCatalog;
import org.apache.flink.cdc.connectors.jdbc.mysql.dialect.MySqlDialect;

/** Discovers the MySQL dialect and its associated metadata catalog. */
public class MySqlJdbcDialectFactory implements JdbcDialectFactory<JdbcSinkConfig> {

    @Override
    public String identifier() {
        return "mysql";
    }

    @Override
    public JdbcDialect createDialect(JdbcSinkConfig config) {
        Preconditions.checkArgument(
                "mysql".equalsIgnoreCase(config.getDialect()),
                "The MySQL JDBC sink cannot handle dialect %s",
                config.getDialect());
        return new MySqlDialect();
    }

    @Override
    public JdbcCatalog createCatalog(JdbcSinkConfig config, JdbcDialect dialect) {
        return new MySqlCatalog(dialect, config);
    }
}
