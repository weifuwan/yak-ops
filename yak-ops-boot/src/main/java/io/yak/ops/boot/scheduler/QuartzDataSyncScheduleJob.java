package io.yak.ops.boot.scheduler;

import io.yak.ops.business.datasync.scheduler.DataSyncScheduleFire;
import io.yak.ops.business.datasync.scheduler.DataSyncScheduleFireListener;
import jakarta.annotation.Resource;
import java.util.Date;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.context.ApplicationContext;

/**
 * 将 Quartz 到点事件转换为框架无关的 Data Sync Schedule Fire，并交回 Data Sync 业务层。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
public class QuartzDataSyncScheduleJob implements Job {

    @Resource
    private ApplicationContext applicationContext;

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Date scheduledFireTime = context.getScheduledFireTime();
        if (scheduledFireTime == null) throw new JobExecutionException("Quartz scheduled fire time is missing");

        try {
            DataSyncScheduleFireListener listener = applicationContext
                    .getBeanProvider(DataSyncScheduleFireListener.class)
                    .getIfAvailable();
            if (listener == null) throw new IllegalStateException("Data Sync schedule fire listener is not available");

            JobDataMap data = context.getMergedJobDataMap();
            listener.onFire(new DataSyncScheduleFire(
                    data.getString(QuartzScheduleEngine.DATA_SCHEDULE_ID),
                    data.getString(QuartzScheduleEngine.DATA_WORKSPACE_ID),
                    data.getString(QuartzScheduleEngine.DATA_TASK_ID),
                    scheduledFireTime.toInstant()));
        } catch (RuntimeException exception) {
            throw new JobExecutionException("Data Sync scheduled fire dispatch failed", exception);
        }
    }
}
