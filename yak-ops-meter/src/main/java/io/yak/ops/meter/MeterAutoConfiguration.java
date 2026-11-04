package io.yak.ops.meter;

import io.yak.ops.meter.metrics.DefaultMetricsProvider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import io.micrometer.core.aop.CountedAspect;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * This configuration class is used to config the metrics. We use <a href="https://micrometer.io/docs/concepts">micrometer</a> as the metrics fade.
 *
 * <p>To open the metrics, you need to set the property "metrics.enabled" to true. Right now, we only support expose the metrics to Prometheus,
 * after you open metrics expose, you can get the metrics data at: http://host:port/actuator/prometheus.
 * <p>You can use the below method to get a meter:
 * <pre>
 *     {@code
 *      Counter counter = Metrics.counter("name", "tag1", "tag2");
 *     }
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy
public class MeterAutoConfiguration {

    @Bean
    public DefaultMetricsProvider metricsProvider(MeterRegistry meterRegistry) {
        return new DefaultMetricsProvider(meterRegistry);
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

    @Bean
    public CountedAspect countedAspect(MeterRegistry registry) {
        return new CountedAspect(registry);
    }
}
