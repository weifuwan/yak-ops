package io.yak.ops.plugin.registry.zookeeper;

import org.apache.commons.lang3.StringUtils;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Configuration
@ConditionalOnProperty(prefix = "registry", name = "type", havingValue = "zookeeper")
@ConfigurationProperties(prefix = "registry")
public class ZookeeperRegistryProperties implements Validator {

    private ZookeeperProperties zookeeper = new ZookeeperProperties();

    private String type;

    @Override
    public boolean supports(Class<?> clazz) {
        return ZookeeperRegistryProperties.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        ZookeeperRegistryProperties zookeeperRegistryProperties = (ZookeeperRegistryProperties) target;
        if (zookeeperRegistryProperties.getZookeeper() == null) {
            errors.rejectValue("zookeeper", "zookeeper", "zookeeper properties is required");
        }

        ZookeeperProperties zookeeper = zookeeperRegistryProperties.getZookeeper();
        if (StringUtils.isEmpty(zookeeper.getNamespace())) {
            errors.rejectValue("zookeeper.namespace", "", "zookeeper.namespace cannot be null");
        }
        if (StringUtils.isEmpty(zookeeper.getConnectString())) {
            errors.rejectValue("zookeeper.connectString", "", "zookeeper.connectString cannot be null");
        }
        if (zookeeper.getRetryPolicy() == null) {
            errors.rejectValue("zookeeper.retryPolicy", "", "zookeeper.retryPolicy cannot be null");
        }
        if (zookeeper.getSessionTimeout() == null || zookeeper.getSessionTimeout().isZero()
                || zookeeper.getSessionTimeout().isNegative()) {
            errors.rejectValue("zookeeper.sessionTimeout", "", "zookeeper.sessionTimeout should be positive");
        }
        if (zookeeper.getConnectionTimeout() == null || zookeeper.getConnectionTimeout().isZero()
                || zookeeper.getConnectionTimeout().isNegative()) {
            errors.rejectValue("zookeeper.connectionTimeout", "", "zookeeper.connectionTimeout should be positive");
        }
        if (zookeeper.getBlockUntilConnected() == null || zookeeper.getBlockUntilConnected().isZero()
                || zookeeper.getBlockUntilConnected().isNegative()) {
            errors.rejectValue("zookeeper.blockUntilConnected", "", "zookeeper.blockUntilConnected should be positive");
        }
        printConfig();
    }

    private void printConfig() {
        String config =
                "\n****************************ZookeeperRegistryProperties**************************************" +
                        "\n  namespace -> " + zookeeper.getNamespace() +
                        "\n  connectString -> " + zookeeper.getConnectString() +
                        "\n  retryPolicy -> " + zookeeper.getRetryPolicy() +
                        "\n  digest -> " + zookeeper.getDigest() +
                        "\n  sessionTimeout -> " + zookeeper.getSessionTimeout() +
                        "\n  connectionTimeout -> " + zookeeper.getConnectionTimeout() +
                        "\n  blockUntilConnected -> " + zookeeper.getBlockUntilConnected() +
                        "\n****************************ZookeeperRegistryProperties**************************************";
        log.info(config);
    }

    @Data
    public static final class ZookeeperProperties {

        private String namespace = "dolphinscheduler";
        private String connectString;
        private RetryPolicy retryPolicy = new RetryPolicy();
        private String digest;
        private Duration sessionTimeout = Duration.ofSeconds(60);
        private Duration connectionTimeout = Duration.ofSeconds(15);
        private Duration blockUntilConnected = Duration.ofSeconds(15);

        @Data
        public static final class RetryPolicy {

            private Duration baseSleepTime = Duration.ofSeconds(1);
            private int maxRetries = 3;
            private Duration maxSleep = Duration.ofSeconds(3);

        }
    }

}
