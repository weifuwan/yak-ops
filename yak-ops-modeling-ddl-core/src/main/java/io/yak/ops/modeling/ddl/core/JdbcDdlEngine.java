package io.yak.ops.modeling.ddl.core;

import org.springframework.stereotype.Component;

/** Default JDBC-backed DDL engine shared by all registered database dialects. */
@Component
public class JdbcDdlEngine extends AbstractDdlEngine {

    public JdbcDdlEngine(DdlDialectRegistry dialectRegistry) {
        super(dialectRegistry);
    }
}
