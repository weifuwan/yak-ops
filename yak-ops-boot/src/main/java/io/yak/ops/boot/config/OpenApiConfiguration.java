package io.yak.ops.boot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Central OpenAPI and Swagger UI configuration for Yak Ops. */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

  @Bean
  public OpenAPI yakOpsOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Yak Ops API")
                .description("Yak Ops Datasource and Security APIs")
                .version("1.0.0"));
  }

  @Bean
  public GroupedOpenApi yakOpsApiGroup() {
    return GroupedOpenApi.builder().group("yak-ops").pathsToMatch("/api/**").build();
  }

  @Bean
  public GroupedOpenApi yakSecurityApiGroup() {
    return GroupedOpenApi.builder()
        .group("yak-security")
        .pathsToMatch("/yak-security/api/**")
        .build();
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "springdoc.swagger-ui",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  SwaggerUiStartupLogger swaggerUiStartupLogger(Environment environment) {
    return new SwaggerUiStartupLogger(environment);
  }
}
