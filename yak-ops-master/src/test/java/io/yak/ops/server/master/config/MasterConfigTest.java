package io.yak.ops.server.master.config;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.server.master.cluster.loadbalancer.WorkerLoadBalancerConfigurationProperties;
import io.yak.ops.server.master.cluster.loadbalancer.WorkerLoadBalancerType;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@AutoConfigureMockMvc
@SpringBootTest(classes = MasterConfig.class)
public class MasterConfigTest {

    @Autowired
    private MasterConfig masterConfig;

    @Test
    public void getServerLoadProtection() {
        MasterServerLoadProtectionConfig serverLoadProtection = masterConfig.getServerLoadProtection();
        assertTrue(serverLoadProtection.isEnabled());
        assertEquals(0.9, serverLoadProtection.getMaxSystemCpuUsagePercentageThresholds());
        assertEquals(0.9, serverLoadProtection.getMaxJvmCpuUsagePercentageThresholds());
        assertEquals(0.9, serverLoadProtection.getMaxJvmCpuUsagePercentageThresholds());
        assertEquals(0.9, serverLoadProtection.getMaxSystemMemoryUsagePercentageThresholds());
        assertEquals(0.9, serverLoadProtection.getMaxDiskUsagePercentageThresholds());
    }

    @Test
    public void getCommandFetchStrategy() {
        CommandFetchStrategy commandFetchStrategy = masterConfig.getCommandFetchStrategy();
        assertThat(commandFetchStrategy.getType())
                .isEqualTo(CommandFetchStrategy.CommandFetchStrategyType.ID_SLOT_BASED);

        CommandFetchStrategy.IdSlotBasedFetchConfig idSlotBasedFetchConfig =
                (CommandFetchStrategy.IdSlotBasedFetchConfig) commandFetchStrategy.getConfig();
        assertThat(idSlotBasedFetchConfig.getIdStep()).isEqualTo(1);
        assertThat(idSlotBasedFetchConfig.getFetchSize()).isEqualTo(10);
    }

    @Test
    public void getWorkerLoadBalancerConfigurationProperties() {
        WorkerLoadBalancerConfigurationProperties workerLoadBalancerConfigurationProperties =
                masterConfig.getWorkerLoadBalancerConfigurationProperties();
        assertThat(workerLoadBalancerConfigurationProperties.getType())
                .isEqualTo(WorkerLoadBalancerType.DYNAMIC_WEIGHTED_ROUND_ROBIN);

        WorkerLoadBalancerConfigurationProperties.DynamicWeightConfigProperties dynamicWeightConfigProperties =
                workerLoadBalancerConfigurationProperties.getDynamicWeightConfigProperties();
        assertThat(dynamicWeightConfigProperties.getMemoryUsageWeight()).isEqualTo(40);
        assertThat(dynamicWeightConfigProperties.getCpuUsageWeight()).isEqualTo(30);
        assertThat(dynamicWeightConfigProperties.getTaskThreadPoolUsageWeight()).isEqualTo(30);
    }

    @Test
    public void getTaskDispatchPolicy() {
        TaskDispatchPolicy policy = masterConfig.getTaskDispatchPolicy();

        assertThat(policy).isNotNull();
        assertThat(policy.isDispatchTimeoutEnabled()).isFalse();
        assertThat(policy.getMaxTaskDispatchDuration()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    public void getKillApplicationWhenTaskFailover() {
        assertThat(masterConfig.isKillApplicationWhenTaskFailover()).isTrue();
    }
}
