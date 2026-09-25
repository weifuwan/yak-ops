package io.yak.ops.plugin.database.elasticsearch;


/** Elasticsearch 8 control-plane datasource profile. */
public final class Elasticsearch8DataSourcePlugin extends AbstractElasticsearchDataSourcePlugin {

    @Override
    public String type() {
        return "ELASTICSEARCH8";
    }

    @Override
    protected String displayName() {
        return "Elasticsearch 8";
    }

    @Override
    protected java.util.Set<String> aliases() {
        return java.util.Set.of("ELASTICSEARCH_8", "ES8");
    }

    @Override
    protected int expectedMajorVersion() {
        return 8;
    }
}
