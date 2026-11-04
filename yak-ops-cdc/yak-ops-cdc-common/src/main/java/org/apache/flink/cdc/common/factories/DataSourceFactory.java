package org.apache.flink.cdc.common.factories;

import org.apache.flink.cdc.common.annotation.PublicEvolving;
import org.apache.flink.cdc.common.source.DataSource;

/**
 * A factory to create {@link DataSource} instances. See also {@link Factory} for more information.
 */
@PublicEvolving
public interface DataSourceFactory extends Factory {

    /** Creates a {@link DataSource} instance. */
    DataSource createDataSource(Context context);
}
