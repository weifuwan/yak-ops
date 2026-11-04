package io.yak.ops.plugin.registry.jdbc;

import io.yak.ops.plugin.registry.jdbc.repository.JdbcRegistryClientRepository;
import io.yak.ops.plugin.registry.jdbc.repository.JdbcRegistryDataChangeEventRepository;
import io.yak.ops.plugin.registry.jdbc.repository.JdbcRegistryDataRepository;
import io.yak.ops.plugin.registry.jdbc.repository.JdbcRegistryLockRepository;
import io.yak.ops.plugin.registry.jdbc.server.IJdbcRegistryServer;
import io.yak.ops.plugin.registry.jdbc.server.JdbcRegistryServer;

import org.apache.ibatis.session.SqlSessionFactory;

import javax.sql.DataSource;

import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zaxxer.hikari.HikariDataSource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ComponentScan
@Configuration(proxyBeanMethods = false)
@MapperScan("io.yak.ops.plugin.registry.jdbc.mapper")
@ConditionalOnProperty(prefix = "registry", name = "type", havingValue = "jdbc")
@AutoConfigureAfter(MybatisPlusAutoConfiguration.class)
public class JdbcRegistryAutoConfiguration {

    public JdbcRegistryAutoConfiguration() {
        log.info("Load JdbcRegistryAutoConfiguration");
    }

    @Bean
    public IJdbcRegistryServer jdbcRegistryServer(JdbcRegistryDataRepository jdbcRegistryDataRepository,
                                                  JdbcRegistryLockRepository jdbcRegistryLockRepository,
                                                  JdbcRegistryClientRepository jdbcRegistryClientRepository,
                                                  JdbcRegistryDataChangeEventRepository jdbcRegistryDataChangeEventRepository,
                                                  JdbcRegistryProperties jdbcRegistryProperties,
                                                  TransactionTemplate jdbcTransactionTemplate) {
        return new JdbcRegistryServer(
                jdbcRegistryDataRepository,
                jdbcRegistryLockRepository,
                jdbcRegistryClientRepository,
                jdbcRegistryDataChangeEventRepository,
                jdbcRegistryProperties,
                jdbcTransactionTemplate);
    }

    @Bean
    public JdbcRegistry jdbcRegistry(JdbcRegistryProperties jdbcRegistryProperties,
                                     IJdbcRegistryServer jdbcRegistryServer) {
        JdbcRegistry jdbcRegistry = new JdbcRegistry(jdbcRegistryProperties, jdbcRegistryServer);
        jdbcRegistry.start();
        return jdbcRegistry;
    }

    @Bean
    @ConditionalOnMissingBean
    public DataSource jdbcRegistryDataSource(JdbcRegistryProperties jdbcRegistryProperties) {
        return new HikariDataSource(jdbcRegistryProperties.getHikariConfig());
    }

    @Bean
    @ConditionalOnMissingBean
    public PlatformTransactionManager jdbcRegistryTransactionManager(DataSource jdbcRegistryDataSource) {
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager();
        transactionManager.setDataSource(jdbcRegistryDataSource);
        return transactionManager;
    }

    @Bean
    @ConditionalOnMissingBean
    public TransactionTemplate jdbcTransactionTemplate(PlatformTransactionManager jdbcRegistryTransactionManager) {
        TransactionTemplate transactionTemplate = new TransactionTemplate();
        transactionTemplate.setTransactionManager(jdbcRegistryTransactionManager);
        return transactionTemplate;
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlSessionFactory jdbcRegistrySqlSessionFactory(DataSource jdbcRegistryDataSource) throws Exception {
        MybatisSqlSessionFactoryBean sqlSessionFactoryBean = new MybatisSqlSessionFactoryBean();
        sqlSessionFactoryBean.setDataSource(jdbcRegistryDataSource);
        return sqlSessionFactoryBean.getObject();
    }

    @Bean
    @ConditionalOnMissingBean
    public SqlSessionTemplate jdbcRegistrySqlSessionTemplate(SqlSessionFactory jdbcRegistrySqlSessionFactory) {
        return new SqlSessionTemplate(jdbcRegistrySqlSessionFactory);
    }

}
