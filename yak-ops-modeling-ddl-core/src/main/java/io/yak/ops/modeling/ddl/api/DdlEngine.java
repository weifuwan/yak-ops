package io.yak.ops.modeling.ddl.api;

import io.yak.ops.modeling.ddl.model.DdlFieldTypeOption;
import io.yak.ops.modeling.ddl.model.DdlPlan;
import io.yak.ops.modeling.ddl.model.DdlRequest;
import io.yak.ops.modeling.ddl.session.DdlSession;
import java.util.List;

/** Database-neutral entry point for DDL preview and execution. */
public interface DdlEngine {

    List<DdlFieldTypeOption> getFieldTypeOptions(String databaseType);

    DdlPlan plan(DdlSession session, DdlRequest request);

    String execute(DdlSession session, DdlRequest request, DdlPlan plan, String publishId)
            throws Exception;
}
