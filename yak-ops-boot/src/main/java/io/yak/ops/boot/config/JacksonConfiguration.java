package io.yak.ops.boot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Yak Ops 全局 Jackson 配置。
 *
 * <p>业务模块可以继续定义自己的专用 ObjectMapper，
 * 未使用 Qualifier 的通用注入点默认使用该 Mapper。</p>
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfiguration {

    @Bean("yakOpsObjectMapper")
    @Primary
    public ObjectMapper yakOpsObjectMapper(Jackson2ObjectMapperBuilder builder) {
        return builder.build();
    }
}
