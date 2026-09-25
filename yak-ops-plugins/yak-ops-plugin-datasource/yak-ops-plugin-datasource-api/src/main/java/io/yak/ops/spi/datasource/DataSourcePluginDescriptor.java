package io.yak.ops.spi.datasource;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 数据源 Provider 的稳定运行时元数据。
 *
 * <p>Provider 自己声明 canonical type、aliases、capabilities 与敏感字段；核心模块不维护数据库类型枚举或 UI schema。</p>
 *
 * @param type Provider canonical type
 * @param aliases 与 canonical type 等价的兼容类型名
 * @param apiVersion Datasource Plugin API 版本
 * @param capabilities Provider 当前实际支持的能力集合
 * @param secretFieldKeys Provider 特有的敏感连接字段名
 * @author weifuwan
 * @since 2026-09-25
 */
public record DataSourcePluginDescriptor(
        String type,
        Set<String> aliases,
        String apiVersion,
        Set<DataSourceCapability> capabilities,
        Set<String> secretFieldKeys) {

    /** 当前运行时接受的 Datasource Plugin API 版本。 */
    public static final String CURRENT_API_VERSION = "3";

    public DataSourcePluginDescriptor {
        type = normalizeType(type);
        aliases = immutableAliases(type, aliases);
        apiVersion = normalize(apiVersion, CURRENT_API_VERSION);
        capabilities = immutableCapabilities(capabilities);
        secretFieldKeys = immutableSecretFieldKeys(secretFieldKeys);
    }

    public boolean matchesType(String value) {
        String normalized = normalizeType(value);
        return type.equals(normalized) || aliases.contains(normalized);
    }

    public boolean supports(DataSourceCapability capability) {
        return capability != null && capabilities.contains(capability);
    }

    public static String normalizeType(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) throw new IllegalArgumentException("plugin type must not be blank");
        return normalized.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static Set<String> immutableAliases(String type, Set<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String alias = normalizeType(value);
            if (!type.equals(alias)) normalized.add(alias);
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static Set<DataSourceCapability> immutableCapabilities(Set<DataSourceCapability> values) {
        if (values == null || values.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    private static Set<String> immutableSecretFieldKeys(Set<String> values) {
        if (values == null || values.isEmpty()) return Set.of();
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String key = trimToNull(value);
            if (key != null) normalized.add(key);
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static String normalize(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
