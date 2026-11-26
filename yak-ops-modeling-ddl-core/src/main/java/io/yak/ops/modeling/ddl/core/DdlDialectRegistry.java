package io.yak.ops.modeling.ddl.core;

import io.yak.ops.modeling.ddl.api.DdlDialect;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Resolves exactly one DDL dialect for a database type. */
@Component
public class DdlDialectRegistry {

    private final Map<String, DdlDialect> dialects = new LinkedHashMap<>();

    public DdlDialectRegistry(List<DdlDialect> dialects) {
        if (dialects == null) {
            return;
        }
        for (DdlDialect dialect : dialects) {
            String key = normalize(dialect.getDatabaseType());
            DdlDialect previous = this.dialects.put(key, dialect);
            if (previous != null) {
                throw new IllegalStateException("重复注册 DDL 方言: " + key);
            }
        }
    }

    public DdlDialect getRequired(String databaseType) {
        String key = normalize(databaseType);
        DdlDialect dialect = dialects.get(key);
        if (dialect == null) {
            throw new IllegalArgumentException("暂不支持该数据源的 DDL 发布: " + databaseType);
        }
        return dialect;
    }

    private String normalize(String databaseType) {
        if (databaseType == null || databaseType.trim().isEmpty()) {
            throw new IllegalArgumentException("DDL 数据库类型不能为空");
        }
        return databaseType.trim().toUpperCase(Locale.ROOT);
    }
}
