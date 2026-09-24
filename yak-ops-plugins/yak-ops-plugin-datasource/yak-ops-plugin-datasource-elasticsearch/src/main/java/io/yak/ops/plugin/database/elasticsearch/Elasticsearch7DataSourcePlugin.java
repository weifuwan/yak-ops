package io.yak.ops.plugin.database.elasticsearch;

import io.yak.ops.common.enums.datasource.DataSourceDbType;

/** Elasticsearch 7 control-plane datasource profile. */
public final class Elasticsearch7DataSourcePlugin extends AbstractElasticsearchDataSourcePlugin {

    @Override
    public DataSourceDbType dbType() {
        return DataSourceDbType.ELASTICSEARCH7;
    }

    @Override
    protected int expectedMajorVersion() {
        return 7;
    }
}
