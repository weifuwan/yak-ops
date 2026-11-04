package io.yak.ops.scheduler.quartz;

import org.quartz.CronTrigger;

public interface QuartzTriggerBuilder {

    CronTrigger build();
}
