package io.yak.ops.boot.config;

import io.yak.ops.boot.scheduler.QuartzSpringBeanJobFactory;
import io.yak.ops.business.datasync.DataSyncScheduleService;
import jakarta.annotation.Resource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 将 Quartz Job 实例接入 Yak Ops Spring Bean 生命周期边界，不在配置层承载 Data Sync 调度业务规则。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Configuration(proxyBeanMethods = false)
public class QuartzSchedulerConfiguration {

    @Resource
    private QuartzSpringBeanJobFactory jobFactory;

    @Bean
    SchedulerFactoryBeanCustomizer quartzSchedulerFactoryBeanCustomizer() {
        return schedulerFactoryBean -> schedulerFactoryBean.setJobFactory(jobFactory);
    }

    @Bean
    ApplicationRunner dataSyncScheduleRuntimeRestore(DataSyncScheduleService scheduleService) {
        return arguments -> scheduleService.restoreScheduleRuntime();
    }
}
