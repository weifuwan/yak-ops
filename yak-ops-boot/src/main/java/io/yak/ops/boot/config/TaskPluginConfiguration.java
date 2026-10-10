package io.yak.ops.boot.config;

import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 在应用启动时发现已装配的 Task Plugin，并拒绝冲突或错误的插件注册。
 *
 * <p>当前注册表只用于任务类型和参数契约校验，不接入现有数据同步业务入口。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
@Configuration(proxyBeanMethods = false)
public class TaskPluginConfiguration {

    @Bean
    TaskPluginRegistry taskPluginRegistry() {
        return new TaskPluginRegistry();
    }
}
