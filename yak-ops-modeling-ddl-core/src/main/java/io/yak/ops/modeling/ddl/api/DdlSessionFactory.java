package io.yak.ops.modeling.ddl.api;

import io.yak.ops.modeling.ddl.session.DdlConnectionInfo;
import io.yak.ops.modeling.ddl.session.DdlSession;

/** Opens a plugin-backed database session without exposing persistence entities to DDL Core. */
public interface DdlSessionFactory {

    DdlSession open(DdlConnectionInfo connectionInfo) throws Exception;
}
