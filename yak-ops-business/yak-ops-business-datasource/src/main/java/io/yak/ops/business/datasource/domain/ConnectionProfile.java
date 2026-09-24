package io.yak.ops.business.datasource.domain;

/** 数据源连接配置值对象。完整连接 JSON 属于敏感信息，禁止通过 toString 输出。 */
public record ConnectionProfile(String jdbcUrl, String normalizedJson, String originalJson) {

    public ConnectionProfile {
        jdbcUrl = normalizeNullable(jdbcUrl);
        normalizedJson = requireText(normalizedJson, "规范化连接参数不能为空");
        originalJson = normalizeNullable(originalJson);
        if (originalJson == null) {
            originalJson = normalizedJson;
        }
    }

    public static ConnectionProfile of(String jdbcUrl, String normalizedJson) {
        return new ConnectionProfile(jdbcUrl, normalizedJson, normalizedJson);
    }

    public boolean hasJdbcUrl() {
        return jdbcUrl != null;
    }

    @Override
    public String toString() {
        return "ConnectionProfile[configured=true, jdbcUrlPresent=" + hasJdbcUrl() + "]";
    }

    private static String requireText(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalizeNullable(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
