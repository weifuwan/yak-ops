package io.yak.ops.dao.plugin.mysql;

import io.yak.ops.dao.plugin.api.DaoPluginConfiguration;
import io.yak.ops.dao.plugin.api.dialect.DatabaseDialect;
import io.yak.ops.dao.plugin.api.monitor.DatabaseMonitor;
import io.yak.ops.dao.plugin.mysql.dialect.MysqlDialect;
import io.yak.ops.dao.plugin.mysql.monitor.MysqlMonitor;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.annotation.DbType;

@Configuration(proxyBeanMethods = false)
@Conditional(MysqlDatabaseEnvironmentCondition.class)
public class MysqlDaoPluginAutoConfiguration implements DaoPluginConfiguration {

    @Autowired
    private DataSource dataSource;

    @Override
    public DbType dbType() {
        return DbType.MYSQL;
    }

    @Override
    public DatabaseMonitor databaseMonitor() {
        return new MysqlMonitor(dataSource);
    }

    @Override
    public DatabaseDialect databaseDialect() {
        return new MysqlDialect(dataSource);
    }
}
