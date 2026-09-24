package io.yak.ops.dao.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一执行 Yak Ops 数据库 Schema Migration。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Configuration(proxyBeanMethods = false)
public class FlywayConfiguration {

    @Bean(name = "yakOpsFlyway", initMethod = "migrate")
    Flyway yakOpsFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/yak-ops")
                .load();
    }
}
