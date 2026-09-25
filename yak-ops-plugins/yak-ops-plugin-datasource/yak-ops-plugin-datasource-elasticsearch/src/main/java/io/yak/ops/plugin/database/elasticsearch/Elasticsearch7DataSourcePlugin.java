package io.yak.ops.plugin.database.elasticsearch;


/** Elasticsearch 7 control-plane datasource profile. */
public final class Elasticsearch7DataSourcePlugin extends AbstractElasticsearchDataSourcePlugin {

    @Override
    public String type() {
        return "ELASTICSEARCH7";
    }

    @Override
    protected String displayName() {
        return "Elasticsearch 7";
    }

    @Override
    protected java.util.Set<String> aliases() {
        return java.util.Set.of("ELASTICSEARCH_7", "ES7");
    }

    @Override
    protected int expectedMajorVersion() {
        return 7;
    }
}
