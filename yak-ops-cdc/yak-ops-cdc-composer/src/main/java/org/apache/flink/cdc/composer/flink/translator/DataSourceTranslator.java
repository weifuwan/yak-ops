package org.apache.flink.cdc.composer.flink.translator;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.cdc.common.configuration.Configuration;
import org.apache.flink.cdc.common.event.Event;
import org.apache.flink.cdc.common.factories.DataSourceFactory;
import org.apache.flink.cdc.common.factories.FactoryHelper;
import org.apache.flink.cdc.common.source.DataSource;
import org.apache.flink.cdc.common.source.EventSourceProvider;
import org.apache.flink.cdc.common.source.FlinkSourceFunctionProvider;
import org.apache.flink.cdc.common.source.FlinkSourceProvider;
import org.apache.flink.cdc.composer.definition.SourceDef;
import org.apache.flink.cdc.composer.flink.FlinkEnvironmentUtils;
import org.apache.flink.cdc.composer.utils.FactoryDiscoveryUtils;
import org.apache.flink.cdc.runtime.typeutils.EventTypeInfo;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.DataStreamSource;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

/** Translator used to build {@link DataSource} which will generate a {@link DataStream}. */
@Internal
public class DataSourceTranslator {

    public DataStream<Event> translate(
            SourceDef sourceDef,
            DataSource dataSource,
            StreamExecutionEnvironment env,
            int sourceParallelism,
            OperatorUidGenerator operatorUidGenerator) {
        // Get source provider
        EventSourceProvider eventSourceProvider = dataSource.getEventSourceProvider();

        return createDataStreamSource(env, eventSourceProvider, sourceDef)
                .setParallelism(sourceParallelism)
                .uid(operatorUidGenerator.generateUid("source"));
    }

    private DataStreamSource<Event> createDataStreamSource(
            StreamExecutionEnvironment env,
            EventSourceProvider eventSourceProvider,
            SourceDef sourceDef) {
        // Source
        if (eventSourceProvider instanceof FlinkSourceProvider) {
            FlinkSourceProvider sourceProvider = (FlinkSourceProvider) eventSourceProvider;
            return env.fromSource(
                    sourceProvider.getSource(),
                    WatermarkStrategy.noWatermarks(),
                    sourceDef.getName().orElse(generateDefaultSourceName(sourceDef)),
                    new EventTypeInfo());
        }

        // SourceFunction
        if (eventSourceProvider instanceof FlinkSourceFunctionProvider) {
            FlinkSourceFunctionProvider sourceFunctionProvider =
                    (FlinkSourceFunctionProvider) eventSourceProvider;
            DataStreamSource<Event> stream =
                    env.addSource(sourceFunctionProvider.getSourceFunction(), new EventTypeInfo());
            sourceDef.getName().ifPresent(stream::name);
            return stream;
        }

        // Unknown provider type
        throw new IllegalStateException(
                String.format(
                        "Unsupported EventSourceProvider type \"%s\"",
                        eventSourceProvider.getClass().getCanonicalName()));
    }

    public DataSource createDataSource(
            SourceDef sourceDef, Configuration pipelineConfig, StreamExecutionEnvironment env) {
        // Search the data source factory
        DataSourceFactory sourceFactory =
                FactoryDiscoveryUtils.getFactoryByIdentifier(
                        sourceDef.getType(), DataSourceFactory.class);
        // Add source JAR to environment
        FactoryDiscoveryUtils.getJarPathByIdentifier(sourceFactory)
                .ifPresent(jar -> FlinkEnvironmentUtils.addJar(env, jar));
        FactoryHelper.DefaultContext context =
                new FactoryHelper.DefaultContext(
                        sourceDef.getConfig(),
                        pipelineConfig,
                        Thread.currentThread().getContextClassLoader(),
                        env.getConfiguration());
        return sourceFactory.createDataSource(context);
    }

    private String generateDefaultSourceName(SourceDef sourceDef) {
        return String.format("Flink CDC Event Source: %s", sourceDef.getType());
    }
}
