package io.yak.ops.common.enums.datasource;

import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Yak Ops 当前支持的数据源类型。具体驱动、端口和 URL 规则由插件提供。 */
@Getter
@RequiredArgsConstructor
public enum DataSourceDbType {
    MYSQL("MySQL"),
    TIDB("TiDB"),
    GOLDENDB("GoldenDB"),
    GBASE8C("GBase 8c"),
    GBASE8A("GBase 8a"),
    GBASE8S("GBase 8s"),
    HANA("SAP HANA"),
    ORACLE("Oracle"),
    POSTGRE_SQL("PostgreSQL"),
    DB2("IBM Db2"),
    OPEN_GAUSS("openGauss"),
    SQL_SERVER("SQL Server"),
    OCEANBASE("OceanBase"),
    YASHAN_DB("YashanDB"),
    HIGHGO("HighGo"),
    IRIS("InterSystems IRIS"),
    XUGU("XuguDB"),
    DUCKDB("DuckDB"),
    DORIS("Doris"),
    STARROCKS("StarRocks"),
    CLICKHOUSE("ClickHouse"),
    ELASTICSEARCH7("Elasticsearch 7"),
    ELASTICSEARCH8("Elasticsearch 8"),
    MONGODB("MongoDB"),
    KINGBASE("KingbaseES"),
    DAMENG("达梦");

    private final String displayName;

    public static DataSourceDbType parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("数据源类型不能为空");
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if ("POSTGRESQL".equals(normalized) || "POSTGRES".equals(normalized)) {
            normalized = "POSTGRE_SQL";
        } else if ("TI_DB".equals(normalized)) {
            normalized = "TIDB";
        } else if ("GOLDEN_DB".equals(normalized) || "ZTE_GOLDENDB".equals(normalized)) {
            normalized = "GOLDENDB";
        } else if ("GBASE_8C".equals(normalized)) {
            normalized = "GBASE8C";
        } else if ("GBASE_8A".equals(normalized)) {
            normalized = "GBASE8A";
        } else if ("GBASE_8S".equals(normalized)) {
            normalized = "GBASE8S";
        } else if ("SAP_HANA".equals(normalized) || "SAPHANA".equals(normalized)) {
            normalized = "HANA";
        } else if ("OPENGAUSS".equals(normalized)) {
            normalized = "OPEN_GAUSS";
        } else if ("SQLSERVER".equals(normalized) || "MSSQL".equals(normalized)) {
            normalized = "SQL_SERVER";
        } else if ("YASHANDB".equals(normalized) || "YASDB".equals(normalized)) {
            normalized = "YASHAN_DB";
        } else if ("HIGH_GO".equals(normalized) || "HGDB".equals(normalized)) {
            normalized = "HIGHGO";
        } else if ("INTERSYSTEMS_IRIS".equals(normalized)) {
            normalized = "IRIS";
        } else if ("XUGUDB".equals(normalized)) {
            normalized = "XUGU";
        } else if ("DUCK_DB".equals(normalized)) {
            normalized = "DUCKDB";
        } else if ("ELASTICSEARCH_7".equals(normalized) || "ES7".equals(normalized)) {
            normalized = "ELASTICSEARCH7";
        } else if ("ELASTICSEARCH_8".equals(normalized) || "ES8".equals(normalized)) {
            normalized = "ELASTICSEARCH8";
        } else if ("MONGO".equals(normalized) || "MONGO_DB".equals(normalized)) {
            normalized = "MONGODB";
        }

        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的数据源类型：" + value, exception);
        }
    }
}
