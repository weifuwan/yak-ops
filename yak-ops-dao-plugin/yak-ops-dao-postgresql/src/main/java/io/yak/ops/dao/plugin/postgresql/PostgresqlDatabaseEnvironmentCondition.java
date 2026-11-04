package io.yak.ops.dao.plugin.postgresql;

import io.yak.ops.dao.plugin.api.DatabaseEnvironmentCondition;

public class PostgresqlDatabaseEnvironmentCondition extends DatabaseEnvironmentCondition {

    public PostgresqlDatabaseEnvironmentCondition() {
        super("postgresql");
    }

}
