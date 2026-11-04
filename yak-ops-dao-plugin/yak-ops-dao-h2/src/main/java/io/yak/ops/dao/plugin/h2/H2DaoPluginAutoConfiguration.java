package io.yak.ops.dao.plugin.h2;

import io.yak.ops.dao.plugin.api.DaoPluginConfiguration;
import io.yak.ops.dao.plugin.api.dialect.DatabaseDialect;
import io.yak.ops.dao.plugin.api.monitor.DatabaseMonitor;
import io.yak.ops.dao.plugin.h2.dialect.H2Dialect;
import io.yak.ops.dao.plugin.h2.monitor.H2Monitor;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.annotation.DbType;

@Conditional(H2DatabaseEnvironmentCondition.class)
@Configuration(proxyBeanMethods = false)
public class H2DaoPluginAutoConfiguration implements DaoPluginConfiguration {

    @Autowired
    private DataSource dataSource;

    @Override
    public DbType dbType() {
        return DbType.H2;
    }

    @Override
    public DatabaseMonitor databaseMonitor() {
        return new H2Monitor(dataSource);
    }

    @Override
    public DatabaseDialect databaseDialect() {
        return new H2Dialect();
    }

}
