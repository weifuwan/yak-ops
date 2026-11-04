package org.apache.flink.cdc.common.udf;

import org.apache.flink.cdc.common.configuration.Configuration;

/** Context for initialization of {@link UserDefinedFunction}. */
public interface UserDefinedFunctionContext {

    Configuration configuration();
}
