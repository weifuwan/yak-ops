package io.yak.ops.modeling.ddl.api;

import io.yak.ops.modeling.ddl.model.DdlFieldTypeOption;
import io.yak.ops.modeling.ddl.model.DdlPlan;
import io.yak.ops.modeling.ddl.model.DdlRequest;
import io.yak.ops.modeling.ddl.session.DdlSession;
import java.util.List;

/** Database-specific DDL capabilities selected by the engine. */
public interface DdlDialect {

    String getDatabaseType();

    List<DdlFieldTypeOption> getFieldTypeOptions();

    void validate(DdlRequest request);

    DdlPlan plan(DdlSession session, DdlRequest request);

    String execute(DdlSession session, DdlRequest request, DdlPlan plan, String publishId)
            throws Exception;
}
