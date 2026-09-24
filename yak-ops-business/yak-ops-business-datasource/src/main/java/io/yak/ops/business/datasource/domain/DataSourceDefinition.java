package io.yak.ops.business.datasource.domain;

import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/** 数据源聚合根。 */
@Getter
@EqualsAndHashCode
@ToString
public class DataSourceDefinition {
    private Long id;
    private String name;
    private DataSourceDbType dbType;

    @ToString.Exclude
    private String jdbcUrl;

    private DataSourceEnvironment environment;
    private DataSourceConnStatus connStatus;
    private String remark;

    @ToString.Exclude
    private String connectionParams;

    @ToString.Exclude
    private String originalJson;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private DataSourceDefinition() {}

    public static DataSourceDefinition create(
            String name,
            DataSourceDbType dbType,
            ConnectionProfile connectionProfile,
            DataSourceEnvironment environment,
            String remark) {
        DataSourceDefinition definition = new DataSourceDefinition();
        definition.name = requireText(name, "数据源名称不能为空");
        definition.dbType = Objects.requireNonNull(dbType, "数据源类型不能为空");
        definition.environment = Objects.requireNonNull(environment, "数据源环境不能为空");
        definition.remark = normalizeNullable(remark);
        definition.replaceConnectionProfile(connectionProfile);
        return definition;
    }

    public static DataSourceDefinition restore(
            Long id,
            String name,
            DataSourceDbType dbType,
            String jdbcUrl,
            DataSourceEnvironment environment,
            DataSourceConnStatus connStatus,
            String remark,
            String connectionParams,
            String originalJson,
            LocalDateTime createTime,
            LocalDateTime updateTime) {
        DataSourceDefinition definition = new DataSourceDefinition();
        definition.id = id;
        definition.name = name;
        definition.dbType = dbType;
        definition.jdbcUrl = jdbcUrl;
        definition.environment = environment;
        definition.connStatus = connStatus;
        definition.remark = remark;
        definition.connectionParams = connectionParams;
        definition.originalJson = originalJson;
        definition.createTime = createTime;
        definition.updateTime = updateTime;
        return definition;
    }

    public void updateConfiguration(
            String name,
            DataSourceDbType requestedType,
            ConnectionProfile connectionProfile,
            DataSourceEnvironment environment,
            String remark) {
        assertTypeUnchanged(requestedType);
        this.name = requireText(name, "数据源名称不能为空");
        this.environment = Objects.requireNonNull(environment, "数据源环境不能为空");
        this.remark = normalizeNullable(remark);
        replaceConnectionProfile(connectionProfile);
    }

    public ConnectionProfile connectionProfile() {
        return new ConnectionProfile(jdbcUrl, connectionParams, originalJson);
    }

    public void replaceConnectionProfile(ConnectionProfile connectionProfile) {
        ConnectionProfile profile = Objects.requireNonNull(connectionProfile, "数据源连接配置不能为空");
        this.jdbcUrl = profile.jdbcUrl();
        this.connectionParams = profile.normalizedJson();
        this.originalJson = profile.originalJson();
        markConnectionUnknown();
    }

    public void assertTypeUnchanged(DataSourceDbType requestedType) {
        DataSourceDbType target = Objects.requireNonNull(requestedType, "数据源类型不能为空");
        if (dbType != null && dbType != target) throw new IllegalArgumentException("编辑数据源时不允许修改数据源类型");
    }

    public void markConnected() {
        connStatus = DataSourceConnStatus.CONNECTED;
    }

    public void markDisconnected() {
        connStatus = DataSourceConnStatus.DISCONNECTED;
    }

    public void markConnectionUnknown() {
        connStatus = DataSourceConnStatus.UNKNOWN;
    }

    private static String requireText(String value, String message) {
        String normalized = normalizeNullable(value);
        if (normalized == null) throw new IllegalArgumentException(message);
        return normalized;
    }

    private static String normalizeNullable(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
