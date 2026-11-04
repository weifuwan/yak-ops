package io.yak.ops.plugin.registry.etcd;

import io.yak.ops.registry.api.Registry;

import javax.net.ssl.SSLException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@ComponentScan
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "registry", name = "type", havingValue = "etcd")
public class EtcdRegistryAutoConfiguration {

    public EtcdRegistryAutoConfiguration() {
        log.info("Load EtcdRegistryAutoConfiguration");
    }

    @Bean
    @ConditionalOnMissingBean(value = Registry.class)
    public EtcdRegistry etcdRegistry(EtcdRegistryProperties etcdRegistryProperties) throws SSLException {
        return new EtcdRegistry(etcdRegistryProperties);
    }

}
