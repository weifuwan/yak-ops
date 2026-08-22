package io.yak.ops.business.sync.realtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.datasource.config.BusinessDatabaseConfiguration;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import java.net.http.HttpClient;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(RealtimeSyncProperties.class)
@Import(BusinessDatabaseConfiguration.class)
@ConditionalOnProperty(prefix = "yak.sync.realtime", name = "enabled", matchIfMissing = true)
public class RealtimeSyncConfiguration {

  @Bean
  SyncExecutionStateMachine syncExecutionStateMachine() {
    return new SyncExecutionStateMachine();
  }

  @Bean(initMethod = "migrate")
  Flyway realtimeSyncFlyway(@Qualifier("yakBusinessDataSource") DataSource dataSource) {
    return Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration/yak-realtime-sync")
        .table("yak_realtime_schema_history")
        .baselineVersion(MigrationVersion.fromVersion("0"))
        .baselineOnMigrate(true)
        .load();
  }

  @Bean(name = "realtimeHttpClient")
  HttpClient realtimeHttpClient(RealtimeSyncProperties properties) {
    return HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
  }

  @Bean(name = "realtimeObjectMapper")
  ObjectMapper realtimeObjectMapper() {
    return new ObjectMapper().findAndRegisterModules();
  }
}
