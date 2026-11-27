package org.apache.flink.cdc.connectors.jdbc.factory;

import org.apache.flink.cdc.common.configuration.ConfigOption;
import org.apache.flink.cdc.common.configuration.Configuration;
import org.apache.flink.cdc.common.factories.DataSourceFactory;
import org.apache.flink.cdc.common.factories.Factory;
import org.apache.flink.cdc.common.factories.FactoryHelper;
import org.apache.flink.cdc.common.pipeline.PipelineOptions;
import org.apache.flink.cdc.common.source.DataSource;
import org.apache.flink.cdc.connectors.jdbc.source.JdbcSourceConfig;
import org.apache.flink.cdc.connectors.jdbc.source.JdbcDataSource;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Factory registered with Flink CDC for the generic bounded JDBC source. */
public final class JdbcDataSourceFactory implements DataSourceFactory {

    @Override
    public String identifier() {
        return "jdbc";
    }

    @Override
    public Set<ConfigOption<?>> requiredOptions() {
        return new LinkedHashSet<>(
                Arrays.asList(JdbcSourceOptions.URL, JdbcSourceOptions.DRIVER_NAME, JdbcSourceOptions.TABLES));
    }

    @Override
    public Set<ConfigOption<?>> optionalOptions() {
        return new LinkedHashSet<>(
                Arrays.asList(
                        JdbcSourceOptions.USERNAME,
                        JdbcSourceOptions.PASSWORD,
                        JdbcSourceOptions.FETCH_SIZE,
                        JdbcSourceOptions.SPLIT_SIZE));
    }

    @Override
    public DataSource createDataSource(Factory.Context context) {
        FactoryHelper.createFactoryHelper(this, context).validate();
        Configuration source = context.getFactoryConfiguration();
        String tables = source.get(JdbcSourceOptions.TABLES);
        Set<String> uniqueTables =
                Arrays.stream(tables.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        long tableCount =
                Arrays.stream(tables.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .count();
        if (uniqueTables.isEmpty()) {
            throw new IllegalArgumentException("source.tables must contain at least one table");
        }
        if (uniqueTables.size() != tableCount) {
            throw new IllegalArgumentException("source.tables must not contain duplicate tables");
        }

        int parallelism =
                context.getPipelineConfiguration().get(PipelineOptions.PIPELINE_PARALLELISM);
        JdbcSourceConfig config =
                new JdbcSourceConfig(
                        source.get(JdbcSourceOptions.URL),
                        source.get(JdbcSourceOptions.DRIVER_NAME),
                        source.get(JdbcSourceOptions.USERNAME),
                        source.get(JdbcSourceOptions.PASSWORD),
                        uniqueTables.stream().collect(Collectors.toList()),
                        source.get(JdbcSourceOptions.FETCH_SIZE),
                        source.get(JdbcSourceOptions.SPLIT_SIZE),
                        parallelism);
        return new JdbcDataSource(config);
    }
}
