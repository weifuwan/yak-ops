package org.apache.flink.cdc.common.factories;

import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.sink.DataSink;

/**
 * A factory to create {@link DataSink} instances. See also {@link Factory} for more information.
 */
@PublicEvolving
public interface DataSinkFactory extends Factory {

    /** Creates a {@link DataSink} instance. */
    DataSink createDataSink(Context context);
}
