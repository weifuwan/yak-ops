package io.yak.ops.boot.scheduler;

import jakarta.annotation.Resource;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
import org.springframework.stereotype.Component;

/**
 * 让 Quartz 创建的 Job 可以使用 Yak Ops Spring Bean，同时保持 Job 生命周期由 Quartz 管理。
 *
 * @author weifuwan
 * @since 2026-09-29
 */
@Component
public class QuartzSpringBeanJobFactory extends SpringBeanJobFactory {

    @Resource
    private AutowireCapableBeanFactory beanFactory;

    @Override
    protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
        Object job = super.createJobInstance(bundle);
        beanFactory.autowireBean(job);
        return job;
    }
}
