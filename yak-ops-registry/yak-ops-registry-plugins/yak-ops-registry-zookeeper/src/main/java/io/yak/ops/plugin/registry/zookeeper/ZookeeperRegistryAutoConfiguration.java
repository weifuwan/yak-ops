package io.yak.ops.plugin.registry.zookeeper;

import io.yak.ops.registry.api.Registry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ComponentScan
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "registry", name = "type", havingValue = "zookeeper")
public class ZookeeperRegistryAutoConfiguration {

    public ZookeeperRegistryAutoConfiguration() {
        log.info("Load ZookeeperRegistryAutoConfiguration");
    }

    @Bean
    @ConditionalOnMissingBean(value = Registry.class)
    public Registry zookeeperRegistry(ZookeeperRegistryProperties zookeeperRegistryProperties) {
        ZookeeperRegistry zookeeperRegistry = new ZookeeperRegistry(zookeeperRegistryProperties);
        zookeeperRegistry.start();
        return zookeeperRegistry;
    }

}
