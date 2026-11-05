package io.yak.ops.api.configuration;

import io.yak.ops.dao.entity.YakOpsVersion;
import io.yak.ops.dao.repository.YakOpsVersionDao;

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
 * The swagger ui is under <a href="http://${host}:${port}/yak-ops/swagger-ui.html">http://${host}:${port}/yak-ops/swagger-ui.html</a>
 */
@Configuration
@ConditionalOnWebApplication
@PropertySource("classpath:swagger.properties")
public class SwaggerConfiguration implements WebMvcConfigurer {

    @Autowired
    private YakOpsVersionDao yakOpsVersionDao;

    private volatile String yakOpsVersion;

    @Bean
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("Yak Ops Api Docs")
                .description("Yak Ops Api Docs")
                .version(getYakOpsVersion());
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

    private String getYakOpsVersion() {
        if (yakOpsVersion != null) {
            return yakOpsVersion;
        }
        yakOpsVersion = yakOpsVersionDao.selectVersion().map(YakOpsVersion::getVersion).orElse("unknown");
        return yakOpsVersion;
    }
}
