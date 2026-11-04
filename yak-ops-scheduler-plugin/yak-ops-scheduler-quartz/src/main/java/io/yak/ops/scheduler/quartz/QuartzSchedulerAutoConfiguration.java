package io.yak.ops.scheduler.quartz;

import io.yak.ops.scheduler.api.SchedulerApi;

import org.quartz.Scheduler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.context.annotation.Bean;

// Right now we only have one implementation of SchedulerApi: QuartzScheduler
// If we have more implementations in the future, we can use @ConditionalOnProperty to distinguish them, rather than using @ConditionalOnClass
@AutoConfiguration(after = {QuartzAutoConfiguration.class})
@ConditionalOnClass(value = Scheduler.class)
public class QuartzSchedulerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SchedulerApi schedulerApi(Scheduler scheduler) {
        return new QuartzScheduler(scheduler);
    }
}
