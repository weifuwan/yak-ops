package io.yak.ops.registry.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RegistryConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RegistryClient registryClient(Registry registry) {
        return new RegistryClient(registry);
    }

}
