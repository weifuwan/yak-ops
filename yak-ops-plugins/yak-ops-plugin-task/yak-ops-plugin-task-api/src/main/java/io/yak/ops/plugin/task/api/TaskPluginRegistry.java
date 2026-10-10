package io.yak.ops.plugin.task.api;

import io.yak.ops.common.util.StringUtils;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * 通过 ServiceLoader 发现 Task 插件，并按 canonical type 解析及验证参数。
 *
 * <p>插件注册必须在启动时完成；重复类型和 Factory/Plugin 类型不一致都直接失败。
 *
 * @author weifuwan
 * @since 2026-10-10
 */
public final class TaskPluginRegistry {

    private final Map<String, TaskPlugin> plugins;

    /** 从当前应用 classpath 加载插件；不存在插件时得到空注册表。 */
    public TaskPluginRegistry() {
        this(loadFactories());
    }

    /** 使用明确提供的 Factory 构建注册表，主要用于隔离测试与受控装配。 */
    public TaskPluginRegistry(Iterable<? extends TaskPluginFactory> factories) {
        if (factories == null) {
            throw new TaskPluginException("Task plugin factories must not be null");
        }
        Map<String, TaskPlugin> discovered = new LinkedHashMap<>();
        for (TaskPluginFactory factory : factories) {
            if (factory == null) {
                throw new TaskPluginException("Task plugin factory must not be null");
            }
            String type = normalizeType(factory.type());
            TaskPlugin plugin = factory.create();
            if (plugin == null || !type.equals(normalizeType(plugin.type()))) {
                throw new TaskPluginException("Task plugin type does not match its factory: " + type);
            }
            if (discovered.putIfAbsent(type, plugin) != null) {
                throw new TaskPluginException("Duplicate task plugin type: " + type);
            }
        }
        plugins = Collections.unmodifiableMap(discovered);
    }

    /** 返回当前 classpath 实际注册的任务类型，不表示运行能力已接入。 */
    public List<String> types() {
        return List.copyOf(plugins.keySet());
    }

    /** 查找已注册插件；未知类型直接失败，不使用默认类型或隐式降级。 */
    public TaskPlugin get(String type) {
        String normalized = normalizeType(type);
        TaskPlugin plugin = plugins.get(normalized);
        if (plugin == null) {
            throw new TaskPluginException("Unknown task plugin type: " + normalized);
        }
        return plugin;
    }

    /** 解析并校验插件配置，任何校验失败都阻断后续业务操作。 */
    public TaskParameters parseParameters(String type, String parametersJson) {
        TaskParameters parameters;
        try {
            parameters = get(type).parseParameters(parametersJson);
        } catch (TaskPluginException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new TaskPluginException("Invalid task plugin parameters for type: " + normalizeType(type), exception);
        }
        if (parameters == null) {
            throw new TaskPluginException("Task plugin returned null parameters: " + normalizeType(type));
        }
        parameters.validate();
        return parameters;
    }

    /** 将类型标准化为大写下划线标识，保持插件类型集合可扩展。 */
    public static String normalizeType(String type) {
        String normalized = StringUtils.trimToNull(type);
        if (normalized == null) {
            throw new TaskPluginException("Task plugin type must not be blank");
        }
        return normalized.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static ServiceLoader<TaskPluginFactory> loadFactories() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = TaskPluginRegistry.class.getClassLoader();
        }
        return ServiceLoader.load(TaskPluginFactory.class, classLoader);
    }
}
