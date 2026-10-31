package io.yak.ops.composer.validation;

/**
 * Maps allowed schema diagnostics into stable product-facing messages.
 *
 * <p>Native database errors and credentials must never be exposed to task consumers.
 */
public final class SchemaValidationMessages {
    private SchemaValidationMessages() {}

    /**
     * Only fixed diagnostic prefixes and simple column identifiers may reach the client.
     * Native driver exceptions, raw DDL, credentials and configuration values are excluded.
     */
    public static String safeSchemaMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message != null) {
            String[][] exactReasons = {
                {
                    "CDC without physical primary keys requires verified target keys for UPSERT",
                    "无物理主键的实时 UPSERT 需要目标主键；请选择来源表中具有非空唯一索引的字段"
                },
                {
                    "JDBC changelog UPSERT requires a source write identity; "
                            + "a snapshot Chunk Key does not identify a unique row",
                    "无物理主键的实时 UPSERT 需要目标主键；请选择来源表中具有非空唯一索引的字段"
                },
                {"JDBC changelog UPSERT requires a target write identity", "按主键更新需要目标表主键，请检查目标主键配置和目标表结构"},
                {"JDBC UPSERT requires row identity on both source and target", "按主键更新需要目标表主键，请检查目标主键配置和目标表结构"},
                {"Target primary key must match an enforced source unique index", "目标主键必须与来源表已建立的非空唯一索引或物理主键一致"},
                {"Unknown target key column", "目标主键字段在来源表中不存在"},
                {"Explicit target primary keys require MySQL source metadata", "自定义目标主键目前仅支持 MySQL 来源表"},
                {"CDC without physical primary keys requires a MySQL target", "无物理主键的实时同步当前仅支持 MySQL 目标库"},
                {"CDC without physical primary keys cannot evolve target schema", "无物理主键的实时同步暂不支持同步结构变更，请关闭该选项"},
                {"JDBC UPSERT requires matching source and target write identity columns", "来源与目标写入主键不一致，请检查主键字段映射"},
                {"JDBC UPSERT does not preserve the full source row identity", "来源与目标写入主键不一致，请检查主键字段映射"},
                {
                    "JDBC RETRACT requires source and target schemas without physical primary keys",
                    "逐行更新要求来源和目标表均无主键；若来源有非空唯一索引，请填写目标主键改用按主键更新"
                },
                {"JDBC RETRACT requires a complete full-row field mapping", "逐行更新要求来源和目标字段完全对应，请检查目标表结构"},
                {
                    "RETRACT transforms require deterministic expressions without event-dependent metadata",
                    "逐行更新的转换和筛选必须只依赖来源行及固定表信息，不能使用当前时间、随机值或事件类型"
                }
            };
            for (String[] reason : exactReasons) {
                if (message.equals(reason[0])) return reason[1];
            }
            if (message.startsWith("Schema change blocked: ")) {
                String detail = message.substring("Schema change blocked: ".length());
                String[] parts = detail.split(": ", 2);
                String reason =
                        switch (parts[0]) {
                            case "PRIMARY_KEY_MISMATCH", "MISSING_KEY_COLUMN" -> "主键与目标表不兼容";
                            case "NULLABILITY_MISMATCH" -> "字段可空规则不兼容";
                            case "UNSAFE_TYPE_CHANGE" -> "字段类型不能安全转换";
                            case "COLUMN_ONLY_IN_TARGET" -> "目标表存在无法映射的字段";
                            case "AMBIGUOUS_IDENTIFIER" -> "字段名大小写存在冲突";
                            case "UNSAFE_COLUMN_PROPERTIES" -> "字段含有不支持自动迁移的物理属性";
                            case "UNSUPPORTED_TYPE", "UNRESOLVED_TYPE" -> "目标数据库不支持该字段类型";
                            default -> "目标表结构不兼容";
                        };
                if (parts.length == 2 && parts[1].matches("[A-Za-z_][A-Za-z_0-9]{0,127}")) {
                    return "字段 " + parts[1] + "：" + reason;
                }
                return reason;
            }
            String[][] reasons = {
                {"Unsupported default expression for column: ", "不支持的默认值表达式"},
                {"Unsupported MySQL native type for column: ", "暂不支持该 MySQL 原生字段类型"},
                {"Unsupported source JDBC type for column: ", "当前 JDBC 读取不支持该字段类型"},
                {"DATA_SYNC source and target column types are incompatible: ", "来源与目标字段类型不兼容"},
                {"DATA_SYNC target column has no source mapping: ", "目标字段没有对应的来源字段"},
                {"Target column comment does not match: ", "目标字段注释不一致"},
                {"Unknown default expression kind for column: ", "无法识别默认值是否为表达式"},
                {"Unsupported default literal escaping for column: ", "默认值含有暂不支持的转义字符"},
                {"Target column properties unavailable: ", "目标字段物理属性缺失"},
                {"CURRENT_TIMESTAMP requires a timestamp column: ", "时间默认值与字段类型不匹配"},
                {"Unsupported ON UPDATE expression for column: ", "暂不支持的自动更新时间表达式"},
                {"AUTO_INCREMENT requires an integer primary key: ", "自增列必须是整型主键"},
                {"Generated column requires a verified expression and write projection: ", "生成列尚不支持安全写入"},
                {"Unknown generation metadata for column: ", "无法确认字段生成规则"},
                {"Unknown target generation metadata for column: ", "无法确认目标字段生成规则"},
                {"Target column properties do not match: ", "目标字段属性与来源不一致"},
                {"Unexpected target column properties: ", "目标表存在不兼容的字段属性"},
                {"Target dialect cannot preserve column properties: ", "目标数据库暂不支持该字段属性"},
                {"Target primary key cannot be nullable: ", "目标主键必须是非空字段"},
                {"Unverified target primary-key comparison: ", "该字段类型暂不支持作为目标主键"},
                {
                    "JDBC RETRACT full-row comparison requires verified database semantics for: ",
                    "逐行更新不支持该字段类型；如有非空唯一索引，请填写目标主键改用按主键更新"
                },
                {"JDBC RETRACT requires equal source and target column definitions: ", "逐行更新要求来源和目标字段类型、顺序及可空性一致"}
            };
            for (String[] reason : reasons) {
                if (!message.startsWith(reason[0])) continue;
                String column = message.substring(reason[0].length());
                if (column.matches("[A-Za-z_][A-Za-z_0-9]{0,127}")) {
                    return "字段 " + column + "：" + reason[1];
                }
            }
        }
        return "目标表结构不兼容，请检查字段类型和默认值等属性";
    }
}
