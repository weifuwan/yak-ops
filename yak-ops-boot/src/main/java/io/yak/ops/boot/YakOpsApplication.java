package io.yak.ops.boot;

import io.yak.ops.plugin.datasource.api.plugin.DataSourcePluginRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Yak Ops application entry point.
 *
 * <p>Yak Ops modules are scanned from their shared root package. Application-level infrastructure
 * wiring is owned by yak-ops-boot.
 *
 * <p>MongoDB is managed by the datasource plugin and creates clients only for explicit datasource
 * operations. Spring Boot's generic Mongo auto-configuration is disabled so merely having the
 * MongoDB driver on the assembled application classpath does not create a default localhost
 * client during startup.
 *
 * <p>Quartz store initialization and startup require verified execution ownership. Generic
 * Quartz auto-configuration is excluded; Cron preview creates no scheduler or store.
 */
@EnableScheduling
@SpringBootApplication(
        scanBasePackages = "io.yak.ops",
        exclude = {MongoAutoConfiguration.class, QuartzAutoConfiguration.class})
public class YakOpsApplication {

    public static void main(String[] args) {
        DataSourcePluginRegistry.load();
        SpringApplication.run(YakOpsApplication.class, args);
    }
}
