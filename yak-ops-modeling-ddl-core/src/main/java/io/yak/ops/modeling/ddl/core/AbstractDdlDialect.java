package io.yak.ops.modeling.ddl.core;

import io.yak.ops.modeling.ddl.api.DdlDialect;
import io.yak.ops.modeling.ddl.model.DdlOptions;
import io.yak.ops.modeling.ddl.model.DdlRequest;

/** Shared validation and option helpers for concrete database dialects. */
public abstract class AbstractDdlDialect implements DdlDialect {

    protected <T extends DdlOptions> T requireOptions(DdlRequest request, Class<T> optionType) {
        if (request == null) {
            throw new IllegalArgumentException("DDL 请求不能为空");
        }
        DdlOptions options = request.getOptions();
        if (!optionType.isInstance(options)) {
            throw new IllegalArgumentException(
                    "DDL 请求参数与数据库方言不匹配，期望: " + optionType.getSimpleName());
        }
        return optionType.cast(options);
    }

}
