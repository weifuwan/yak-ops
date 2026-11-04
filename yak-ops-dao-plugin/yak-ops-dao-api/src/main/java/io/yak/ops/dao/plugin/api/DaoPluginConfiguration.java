package io.yak.ops.dao.plugin.api;

import io.yak.ops.dao.plugin.api.dialect.DatabaseDialect;
import io.yak.ops.dao.plugin.api.monitor.DatabaseMonitor;

import com.baomidou.mybatisplus.annotation.DbType;

/**
 * DaoPluginConfiguration used to configure the dao plugin.
 */
public interface DaoPluginConfiguration {

    DbType dbType();

    DatabaseMonitor databaseMonitor();

    DatabaseDialect databaseDialect();

}
