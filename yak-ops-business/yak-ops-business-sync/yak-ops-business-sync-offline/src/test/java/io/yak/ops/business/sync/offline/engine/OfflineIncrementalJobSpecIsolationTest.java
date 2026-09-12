package io.yak.ops.business.sync.offline.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OfflineIncrementalJobSpecIsolationTest {

  @Test
  void incrementalControlPlaneConfigNeverEntersLinkUpJobSpec() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode definition =
        objectMapper.readTree(
            """
            {
              "basic":{"mode":"GUIDE_SINGLE"},
              "source":{"connectorId":"jdbc","config":{"table":"orders"}},
              "sink":{"connectorId":"jdbc","config":{"table":"orders"}},
              "incremental":{"enabled":true,"column":"updated_at"}
            }
            """);

    JsonNode jobSpecInput = OfflineDefinitionModelAdapter.forJobSpec(definition, objectMapper);

    assertThat(jobSpecInput.has("incremental")).isFalse();
    assertThat(definition.has("incremental")).isTrue();
  }
}
