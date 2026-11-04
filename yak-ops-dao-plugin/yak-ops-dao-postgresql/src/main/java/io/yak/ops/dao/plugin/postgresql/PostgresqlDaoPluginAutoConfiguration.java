package io.yak.ops.dao.plugin.postgresql;

import io.yak.ops.dao.plugin.api.DaoPluginConfiguration;
import io.yak.ops.dao.plugin.api.dialect.DatabaseDialect;
import io.yak.ops.dao.plugin.api.monitor.DatabaseMonitor;
import io.yak.ops.dao.plugin.postgresql.dialect.PostgresqlDialect;
import io.yak.ops.dao.plugin.postgresql.monitor.PostgresqlMonitor;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.annotation.DbType;

@Conditional(PostgresqlDatabaseEnvironmentCondition.class)
@Configuration(proxyBeanMethods = false)
public class PostgresqlDaoPluginAutoConfiguration implements DaoPluginConfiguration {

    @Autowired
    private DataSource dataSource;

    @Override
    public DbType dbType() {
        return DbType.POSTGRE_SQL;
    }

    @Override
    public DatabaseMonitor databaseMonitor() {
        return new PostgresqlMonitor(dataSource);
    }

    @Override
    public DatabaseDialect databaseDialect() {
        return new PostgresqlDialect(dataSource);
    }
}
