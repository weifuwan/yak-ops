package io.yak.ops.modeling.ddl.core;

import io.yak.ops.modeling.ddl.api.DdlDialect;
import io.yak.ops.modeling.ddl.api.DdlEngine;
import io.yak.ops.modeling.ddl.model.DdlFieldTypeOption;
import io.yak.ops.modeling.ddl.model.DdlPlan;
import io.yak.ops.modeling.ddl.model.DdlRequest;
import io.yak.ops.modeling.ddl.session.DdlSession;
import java.util.List;

/** Template implementation that routes requests to the dialect selected by the session. */
public abstract class AbstractDdlEngine implements DdlEngine {

    private final DdlDialectRegistry dialectRegistry;

    protected AbstractDdlEngine(DdlDialectRegistry dialectRegistry) {
        this.dialectRegistry = dialectRegistry;
    }

    @Override
    public List<DdlFieldTypeOption> getFieldTypeOptions(String databaseType) {
        return dialectRegistry.getRequired(databaseType).getFieldTypeOptions();
    }

    @Override
    public DdlPlan plan(DdlSession session, DdlRequest request) {
        DdlDialect dialect = resolveDialect(session);
        dialect.validate(request);
        return dialect.plan(session, request);
    }

    @Override
    public String execute(DdlSession session, DdlRequest request, DdlPlan plan, String publishId)
            throws Exception {
        DdlDialect dialect = resolveDialect(session);
        if (request == null || plan == null) {
            throw new IllegalArgumentException("DDL 执行请求和执行计划不能为空");
        }
        return dialect.execute(session, request, plan, publishId);
    }

    protected DdlDialect resolveDialect(DdlSession session) {
        if (session == null) {
            throw new IllegalArgumentException("DDL 数据库会话不能为空");
        }
        return dialectRegistry.getRequired(session.getDatabaseType());
    }
}
