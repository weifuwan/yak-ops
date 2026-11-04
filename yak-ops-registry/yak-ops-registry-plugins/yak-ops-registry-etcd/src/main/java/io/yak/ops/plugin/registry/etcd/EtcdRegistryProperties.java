package io.yak.ops.plugin.registry.etcd;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "registry")
public class EtcdRegistryProperties {

    private String endpoints;
    private String namespace = "yak-ops";
    private Duration connectionTimeout = Duration.ofSeconds(9);

    private Duration ttl = Duration.ofSeconds(30);

    // auth
    private String user;
    private String password;
    private String authority;

    // RetryPolicy
    private Duration retryDelay = Duration.ofMillis(60);
    private Duration retryMaxDelay = Duration.ofMillis(300);
    private Duration retryMaxDuration = Duration.ofMillis(1500);

    // loadBalancerPolicy
    private String loadBalancerPolicy;

    // ssl
    private String certFile;
    private String keyCertChainFile;
    private String keyFile;
}
