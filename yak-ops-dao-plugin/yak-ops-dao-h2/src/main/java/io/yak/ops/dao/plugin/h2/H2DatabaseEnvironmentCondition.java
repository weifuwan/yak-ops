package io.yak.ops.dao.plugin.h2;

import io.yak.ops.dao.plugin.api.DatabaseEnvironmentCondition;

public class H2DatabaseEnvironmentCondition extends DatabaseEnvironmentCondition {

    public H2DatabaseEnvironmentCondition() {
        super("h2");
    }

}
