package io.yak.ops.boot.config;

import io.yak.ops.business.task.submission.ScheduleAdmissionController;
import io.yak.ops.business.task.submission.SubmissionService;
import io.yak.ops.plugin.scheduler.api.CronService;
import io.yak.ops.plugin.scheduler.api.Scheduler;
import io.yak.ops.plugin.scheduler.quartz.QuartzCronService;
import io.yak.ops.plugin.scheduler.quartz.QuartzScheduler;
import java.time.Clock;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Assembles scheduler collaborators without initializing its store before verified ownership. */
@Configuration(proxyBeanMethods = false)
public class ScheduleConfiguration {
    @Bean
    static org.springframework.beans.factory.config.BeanFactoryPostProcessor registryMigrationOrder() {
        return factory -> {
            if (factory.containsBeanDefinition("jdbcRegistry")) {
                factory.getBeanDefinition("jdbcRegistry").setDependsOn("yakOpsFlyway");
            }
        };
    }

    @Bean
    Clock scheduleClock() {
        return Clock.systemUTC();
    }

    @Bean
    CronService scheduleCronService() {
        return new QuartzCronService();
    }

    @Bean
    ScheduleAdmissionController scheduleAdmissionController(
            Clock scheduleClock, @Value("${yak.schedule.max-lateness:PT60S}") Duration maxLateness) {
        return new ScheduleAdmissionController(scheduleClock, maxLateness);
    }

    @Bean(destroyMethod = "")
    Scheduler scheduleEngine(
            DataSource dataSource, SubmissionService submissions, ScheduleAdmissionController admissionController) {
        return new QuartzScheduler(
                dataSource, trigger -> submissions.submitScheduled(trigger), admissionController.maxLateness());
    }
}
