package io.yak.ops.server.master.cluster.loadbalancer;

import org.springframework.validation.Errors;

import lombok.Data;

@Data
public class WorkerLoadBalancerConfigurationProperties {

    private WorkerLoadBalancerType type = WorkerLoadBalancerType.ROUND_ROBIN;

    private DynamicWeightConfigProperties dynamicWeightConfigProperties = new DynamicWeightConfigProperties();

    public void validate(Errors errors) {
        dynamicWeightConfigProperties.validated(errors);
    }

    @Data
    public static class DynamicWeightConfigProperties {

        private int cpuUsageWeight = 30;

        private int memoryUsageWeight = 30;

        private int taskThreadPoolUsageWeight = 40;

        public void validated(Errors errors) {
            if (cpuUsageWeight < 0) {
                errors.rejectValue("cpuUsageWeight", "cpuUsageWeight", "cpuUsageWeight must >= 0");
            }
            if (memoryUsageWeight < 0) {
                errors.rejectValue("memoryUsageWeight", "memoryUsageWeight", "memoryUsageWeight must >= 0");
            }
            if (taskThreadPoolUsageWeight < 0) {
                errors.rejectValue("threadUsageWeight", "threadUsageWeight", "threadUsageWeight must >= 0");
            }
            if (cpuUsageWeight + memoryUsageWeight + taskThreadPoolUsageWeight != 100) {
                errors.rejectValue("cpuUsageWeight", "cpuUsageWeight",
                        "cpuUsageWeight + memoryUsageWeight + threadUsageWeight must be 100");
            }
        }

    }
}
