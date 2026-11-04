package io.yak.ops.dao.plugin.mysql;

import io.yak.ops.dao.plugin.api.DatabaseEnvironmentCondition;

public class MysqlDatabaseEnvironmentCondition extends DatabaseEnvironmentCondition {

    public MysqlDatabaseEnvironmentCondition() {
        super("mysql");
    }

}
