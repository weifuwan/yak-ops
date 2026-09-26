package io.yak.ops.boot.config;

import io.yak.ops.boot.workspace.WorkspaceContextInterceptor;
import io.yak.ops.business.workspace.WorkspaceService;
import io.yak.ops.security.authentication.AuthenticationManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 将 Workspace 请求上下文校验接入 Yak Ops Web Runtime。
 *
 * @author weifuwan
 * @since 2026-09-26
 */
@Configuration(proxyBeanMethods = false)
public class WorkspaceConfiguration {

    private static final int WORKSPACE_INTERCEPTOR_ORDER = 100;

    @Bean
    WebMvcConfigurer workspaceWebMvcConfigurer(
            AuthenticationManager authenticationManager, WorkspaceService workspaceService) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new WorkspaceContextInterceptor(authenticationManager, workspaceService))
                        .addPathPatterns("/**")
                        .order(WORKSPACE_INTERCEPTOR_ORDER);
            }
        };
    }
}
