package io.yak.ops.plugin.database.elasticsearch;

import io.yak.ops.common.enums.datasource.DataSourceDbType;

/** Elasticsearch 8 control-plane datasource profile. */
public final class Elasticsearch8DataSourcePlugin extends AbstractElasticsearchDataSourcePlugin {

    @Override
    public DataSourceDbType dbType() {
        return DataSourceDbType.ELASTICSEARCH8;
    }

    @Override
    protected int expectedMajorVersion() {
        return 8;
    }
}
