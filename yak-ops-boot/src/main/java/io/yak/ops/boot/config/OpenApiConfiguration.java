package io.yak.ops.boot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一配置 Yak Ops OpenAPI 文档。
 *
 * @author weifuwan
 * @since 2026-09-24
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI yakOpsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Yak Ops API")
                        .description("Yak Ops HTTP API")
                        .version("v1"));
    }
}
