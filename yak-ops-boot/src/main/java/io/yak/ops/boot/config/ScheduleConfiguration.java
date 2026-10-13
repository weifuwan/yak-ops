package io.yak.ops.boot.config;

import io.yak.ops.plugin.scheduler.api.CronService;
import io.yak.ops.plugin.scheduler.quartz.QuartzCronService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Assembles time-rule evaluation without initializing an execution scheduler. */
@Configuration(proxyBeanMethods = false)
public class ScheduleConfiguration {
    @Bean
    CronService scheduleCronService() {
        return new QuartzCronService();
    }
}
