package org.apache.flink.cdc.composer.flink;

import org.apache.flink.cdc.common.annotation.Internal;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.PipelineOptions;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Utilities for {@link org.apache.flink.streaming.api.environment.StreamExecutionEnvironment}. */
@Internal
public class FlinkEnvironmentUtils {
    private static final Logger LOG = LoggerFactory.getLogger(FlinkEnvironmentUtils.class);

    private FlinkEnvironmentUtils() {}

    /**
     * Add the specified JAR to {@link StreamExecutionEnvironment} so that the JAR will be uploaded
     * together with the job graph.
     */
    public static void addJar(StreamExecutionEnvironment env, URL jarUrl) {
        addJar(env, Collections.singletonList(jarUrl));
    }

    /**
     * Add the specified JARs to {@link StreamExecutionEnvironment} so that the JAR will be uploaded
     * together with the job graph.
     */
    public static void addJar(StreamExecutionEnvironment env, Collection<URL> jarUrls) {
        try {
            Class<StreamExecutionEnvironment> envClass = StreamExecutionEnvironment.class;
            Field field = envClass.getDeclaredField("configuration");
            field.setAccessible(true);
            Configuration configuration = ((Configuration) field.get(env));
            List<String> previousJars =
                    configuration.getOptional(PipelineOptions.JARS).orElse(new ArrayList<>());
            List<String> currentJars =
                    Stream.concat(previousJars.stream(), jarUrls.stream().map(URL::toString))
                            .distinct()
                            .collect(Collectors.toList());
            LOG.info("pipeline.jars is {}", String.join(",", currentJars));
            configuration.set(PipelineOptions.JARS, currentJars);
        } catch (Exception e) {
            throw new RuntimeException("Failed to add JAR to Flink execution environment", e);
        }
    }
}
