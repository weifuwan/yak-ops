package io.yak.ops.modeling.ddl.doris.planning;

import io.yak.ops.modeling.ddl.doris.model.ReplicationPlan;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Inspects Doris storage backends before generating a physical CREATE TABLE statement. */
@Component
public class ReplicationPolicy {

    private static final int DEFAULT_REPLICATION_NUM = 3;
    private static final int MAX_MANUAL_REPLICATION_NUM = 3;
    private static final Pattern LOCATION_TAG =
            Pattern.compile("\"location\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    public ReplicationPlan resolve(Connection connection, Integer requestedReplicationNum) {
        validateRequestedReplicationNum(requestedReplicationNum);

        final int availableBackendCount;
        try {
            availableBackendCount = countAvailableDefaultTagBackends(connection);
        } catch (Exception exception) {
            if (requestedReplicationNum == null) {
                throw new IllegalArgumentException(
                        "无法自动检测 Doris 集群可用 BE 数量，可能是当前数据源账号没有 SHOW BACKENDS 查询权限。"
                                + "请切换为手动指定副本数（1～3），或检查数据源账号权限。",
                        exception);
            }
            return new DorisReplicationPlan(
                    requestedReplicationNum,
                    null,
                    false,
                    "无法自动读取 Doris 集群 BE 状态，本次按手动设置的 "
                            + requestedReplicationNum
                            + " 个副本生成 DDL，系统未能校验集群容量。请确保默认 Tag 下有足够的可用 BE。");
        }

        if (availableBackendCount <= 0) {
            throw new IllegalArgumentException(
                    "Doris 集群默认 Tag 下没有可用 BE，无法创建物理表。请检查 BE 的 Alive、"
                            + "SystemDecommissioned 和 Tag 状态。");
        }

        if (requestedReplicationNum == null) {
            int resolvedReplicationNum = Math.min(DEFAULT_REPLICATION_NUM, availableBackendCount);
            String warning = resolvedReplicationNum == 1
                    ? "当前集群默认 Tag 下只有 1 个可用 BE，已自动设置为单副本。单副本不具备数据冗余，建议仅用于开发和测试环境。"
                    : null;
            return new DorisReplicationPlan(
                    resolvedReplicationNum, availableBackendCount, true, warning);
        }

        if (requestedReplicationNum > availableBackendCount) {
            throw new IllegalArgumentException(
                    "所选副本数为 " + requestedReplicationNum
                            + "，但 Doris 默认 Tag 下只有 " + availableBackendCount
                            + " 个可用 BE。请将副本数调整为不超过可用 BE 数量，或检查集群节点状态。");
        }

        String warning = requestedReplicationNum == 1
                ? "当前使用单副本配置，不具备数据冗余，建议仅用于开发和测试环境。"
                : null;
        return new DorisReplicationPlan(
                requestedReplicationNum, availableBackendCount, false, warning);
    }

    private int countAvailableDefaultTagBackends(Connection connection) throws SQLException {
        int count = 0;
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SHOW BACKENDS")) {
            ResultSetMetaData metadata = resultSet.getMetaData();
            Set<String> columns = new HashSet<>();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                columns.add(metadata.getColumnLabel(i).toLowerCase(Locale.ROOT));
            }
            requireColumn(columns, "alive");
            requireColumn(columns, "systemdecommissioned");
            requireColumn(columns, "tag");

            boolean hasClusterDecommissioned = columns.contains("clusterdecommissioned");
            boolean hasNodeRole = columns.contains("noderole");
            boolean hasLoadAvailable = columns.contains("isloadavailable");

            while (resultSet.next()) {
                if (!isTrue(resultSet.getString("Alive"))
                        || isTrue(resultSet.getString("SystemDecommissioned"))) {
                    continue;
                }
                if (hasClusterDecommissioned
                        && isTrue(resultSet.getString("ClusterDecommissioned"))) {
                    continue;
                }
                if (hasNodeRole
                        && "COMPUTATION".equalsIgnoreCase(resultSet.getString("NodeRole"))) {
                    continue;
                }
                if (hasLoadAvailable && !isTrue(resultSet.getString("IsLoadAvailable"))) {
                    continue;
                }
                if (isDefaultLocationTag(resultSet.getString("Tag"))) {
                    count++;
                }
            }
        }
        return count;
    }

    private void requireColumn(Set<String> columns, String name) throws SQLException {
        if (!columns.contains(name)) {
            throw new SQLException("SHOW BACKENDS 结果缺少必要列: " + name);
        }
    }

    private boolean isDefaultLocationTag(String tag) {
        if (tag == null) {
            return false;
        }
        Matcher matcher = LOCATION_TAG.matcher(tag);
        return matcher.find() && "default".equalsIgnoreCase(matcher.group(1));
    }

    private boolean isTrue(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private void validateRequestedReplicationNum(Integer replicationNum) {
        if (replicationNum != null
                && (replicationNum < 1 || replicationNum > MAX_MANUAL_REPLICATION_NUM)) {
            throw new IllegalArgumentException("Doris 副本数只能设置为 1、2 或 3");
        }
    }
}
