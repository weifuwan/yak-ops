package io.yak.ops.dao;

import io.yak.ops.dao.plugin.api.DaoPluginConfiguration;
import io.yak.ops.dao.plugin.api.dialect.DatabaseDialect;
import io.yak.ops.dao.plugin.api.monitor.DatabaseMonitor;

import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

@Configuration
@ComponentScan("io.yak.ops.dao")
@EnableAutoConfiguration
@MapperScan(basePackages = "io.yak.ops.dao.mapper", sqlSessionFactoryRef = "sqlSessionFactory")
public class DaoConfiguration {

    /**
     * Inject this field to make sure the database is initialized, this can solve the table not found issue #8432.
     */
    @Autowired(required = false)
    public DataSourceScriptDatabaseInitializer dataSourceScriptDatabaseInitializer;

    /**
     * Inject this field to make sure the DaoPluginConfiguration is initialized before SpringConnectionFactory.
     */
    @Autowired
    public DaoPluginConfiguration daoPluginConfiguration;

    @Bean
    public MybatisPlusInterceptor paginationInterceptor(DbType dbType) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(dbType));
        return interceptor;
    }

    @Bean
    public DatabaseIdProvider databaseIdProvider() {
        return new VendorDatabaseIdProvider();
    }

    @Bean
    public DbType dbType() {
        return daoPluginConfiguration.dbType();
    }

    @Bean
    public DatabaseMonitor databaseMonitor() {
        return daoPluginConfiguration.databaseMonitor();
    }

    @Bean
    public DatabaseDialect databaseDialect() {
        return daoPluginConfiguration.databaseDialect();
    }

}
