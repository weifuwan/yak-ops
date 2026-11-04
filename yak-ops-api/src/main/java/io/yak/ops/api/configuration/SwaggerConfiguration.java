package io.yak.ops.api.configuration;

import io.yak.ops.dao.entity.DsVersion;
import io.yak.ops.dao.repository.DsVersionDao;

import org.springdoc.core.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/**
 * Swagger configuration, only enabled when the configuration item api.swagger.enable is true.
 * The swagger ui is under <a href="http://${host}:${port}/dolphinscheduler/swagger-ui.html">http://${host}:${port}/dolphinscheduler/swagger-ui.html</a>
 */
@Configuration
@ConditionalOnWebApplication
@PropertySource("classpath:swagger.properties")
public class SwaggerConfiguration implements WebMvcConfigurer {

    @Autowired
    private DsVersionDao dsVersionDao;

    private volatile String dsVersion;

    @Bean
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("Apache DolphinScheduler Api Docs")
                .description("Apache DolphinScheduler Api Docs")
                .version(getDsVersion());
        return new OpenAPI().info(info);
    }

    @Bean
    public GroupedOpenApi publicApi1() {
        return GroupedOpenApi.builder()
                .group("v1")
                .pathsToExclude("/v2/**")
                .build();
    }

    @Bean
    public GroupedOpenApi publicApi2() {
        return GroupedOpenApi.builder()
                .group("v2")
                .pathsToMatch("/v2/**")
                .build();
    }

    private String getDsVersion() {
        if (dsVersion != null) {
            return dsVersion;
        }
        dsVersion = dsVersionDao.selectVersion().map(DsVersion::getVersion).orElse("unknown");
        return dsVersion;
    }
}
