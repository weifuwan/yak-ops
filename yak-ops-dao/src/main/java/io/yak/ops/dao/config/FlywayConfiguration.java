package io.yak.ops.dao.config;

import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Unified Flyway configuration for the Yak Ops schema. */
@Configuration(proxyBeanMethods = false)
public class FlywayConfiguration {

    @Bean(name = "yakOpsFlyway", initMethod = "migrate")
    Flyway yakOpsFlyway(
            DataSource dataSource,
            @Value("${yak.security.application-name:${spring.application.name:yak-ops}}") String applicationName) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/yak-ops")
                .placeholders(Map.of("appName", applicationName))
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .load();
    }
}
