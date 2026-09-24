package io.yak.ops.boot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;

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
 */
@SpringBootApplication(scanBasePackages = "io.yak.ops", exclude = MongoAutoConfiguration.class)
public class YakOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(YakOpsApplication.class, args);
    }
}
