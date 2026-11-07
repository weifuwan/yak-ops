package io.yak.ops.plugin.task.datasync;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.common.utils.JSONUtils;
import io.yak.ops.plugin.task.api.enums.ResourceType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.ObjectNode;

class DataSyncParametersTest {

    @Test
    void acceptsNativePipelineYamlTreeAndExtractsResources() {
        DataSyncParameters parameters = valid();
        assertTrue(parameters.checkParameters());
        assertTrue(parameters.getResources().getResourceMap(ResourceType.DATASOURCE).containsKey(1));
        assertTrue(parameters.getResources().getResourceMap(ResourceType.DATASOURCE).containsKey(2));
    }

    @Test
    void rejectsUnsupportedConnectorsAndRuntimeModes() {
        DataSyncParameters parameters = valid();
        ((ObjectNode) parameters.getDefinition().path("source")).put("type", "postgres");
        assertFalse(parameters.checkParameters());

        parameters = valid();
        ((ObjectNode) parameters.getDefinition().path("pipeline")).put("execution.runtime-mode", "BATCH");
        assertFalse(parameters.checkParameters());
    }

    @Test
    void acceptsAdditionalFlinkConnectorOptions() {
        DataSyncParameters parameters = valid();
        ((ObjectNode) parameters.getDefinition().path("source"))
                .put("scan.incremental.snapshot.chunk.size", 4096);
        assertTrue(parameters.checkParameters());
    }

    @Test
    void rejectsInlineCredentials() {
        DataSyncParameters parameters = valid();
        ((ObjectNode) parameters.getDefinition().path("source")).put("password", "do-not-save");
        assertFalse(parameters.checkParameters());
        assertThrows(IllegalArgumentException.class, parameters::validate);
    }

    @Test
    void rejectsMissingDropProtectionAndTransform() {
        DataSyncParameters parameters = valid();
        ((ObjectNode) parameters.getDefinition().path("sink")).remove("exclude.schema.changes");
        assertFalse(parameters.checkParameters());
        parameters = valid();
        ((ObjectNode) parameters.getDefinition()).putArray("transform")
                .addObject().put("projection", "*");
        assertFalse(parameters.checkParameters());
    }

    @Test
    void rejectsRouteMismatches() {
        DataSyncParameters parameters = valid();
        ((ObjectNode) parameters.getDefinition().path("route").get(0)).put("source-table", "src_db.other");
        assertFalse(parameters.checkParameters());
    }

    static DataSyncParameters valid() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("type", "mysql");
        source.put("datasource-id", "1");
        source.put("tables", "\\Qsrc_db.orders\\E");
        source.put("scan.startup.mode", "initial");
        Map<String, Object> sink = new LinkedHashMap<>();
        sink.put("type", "jdbc");
        sink.put("datasource-id", "2");
        sink.put("exclude.schema.changes", List.of("drop.column"));
        Map<String, Object> route = Map.of("source-table", "\\Qsrc_db.orders\\E",
                "sink-table", "dst_db.orders");
        Map<String, Object> pipeline = Map.of(
                "execution.runtime-mode", "STREAMING",
                "parallelism", 1,
                "schema.change.behavior", "evolve");
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("source", source);
        definition.put("sink", sink);
        definition.put("route", List.of(route));
        definition.put("pipeline", pipeline);
        return JSONUtils.parseObject(
                JSONUtils.toJsonString(Map.of("definition", definition)), DataSyncParameters.class);
    }
}
