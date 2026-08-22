package io.yak.ops.business.sync.realtime.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecValidator;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;

class RealtimeYamlCodecTest {

  private final RealtimeYamlCodec codec =
      new RealtimeYamlCodec(
          Validation.buildDefaultValidatorFactory().getValidator(),
          new CdcPipelineSpecValidator());

  @Test
  void parsesConciseYamlAndAppliesStableDefaults() {
    String yaml =
        """
        version: 1
        source:
          dataSourceRef: 1
        sink:
          dataSourceRef: 2
        tables:
          - sourceTable: orders
            keyColumns: [id]
        """;

    CdcPipelineSpec spec = codec.parse(yaml);

    assertThat(spec.sourceDataSourceRef()).isEqualTo(1L);
    assertThat(spec.sinkDataSourceRef()).isEqualTo(2L);
    assertThat(spec.startupMode()).isEqualTo("initial");
    assertThat(spec.schemaEvolution()).isEqualTo(CdcPipelineSpec.SchemaEvolution.EVOLVE);
    assertThat(spec.parallelism()).isEqualTo(1);
    assertThat(spec.tables()).containsExactly(
        new CdcPipelineSpec.TableRoute(
            "orders", "orders", CdcPipelineSpec.MatchMode.EXACT, List.of("id")));
    assertThat(spec.sink().batchSize()).isEqualTo(1_000);
    assertThat(spec.sink().strictReplaySafety()).isTrue();
  }

  @Test
  void renderAndParsePreserveCanonicalSpec() {
    CdcPipelineSpec spec =
        new CdcPipelineSpec(
            11L,
            22L,
            List.of(
                new CdcPipelineSpec.TableRoute(
                    "orders", "public.ods_orders", CdcPipelineSpec.MatchMode.EXACT, List.of("id")),
                new CdcPipelineSpec.TableRoute(
                    "user_.*", "public.users", CdcPipelineSpec.MatchMode.REGEX, List.of("id"))),
            "latest-offset",
            CdcPipelineSpec.SchemaEvolution.IGNORE,
            4,
            90_000,
            new CdcPipelineSpec.RestartPolicy("fixed-delay", 5, 15_000),
            new CdcPipelineSpec.SinkTuning(7, 2_000, 3_000, 8_388_608, 256, true));

    String yaml = codec.render(spec);
    CdcPipelineSpec reparsed = codec.parse(yaml);

    assertThat(yaml)
        .contains(
            "version: 1",
            "dataSourceRef: 11",
            "sinkTable: public.ods_orders",
            "startupMode: latest-offset",
            "parallelism: 4");
    assertThat(reparsed).isEqualTo(spec);
  }

  @Test
  void rejectsUnknownConnectionOrCredentialFields() {
    String yaml =
        """
        version: 1
        source:
          dataSourceRef: 1
          password: secret
        sink:
          dataSourceRef: 2
        tables:
          - sourceTable: orders
            keyColumns: [id]
        """;

    assertThatThrownBy(() -> codec.parse(yaml))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("password");
  }

  @Test
  void rejectsTableWithoutReplayKey() {
    String yaml =
        """
        source:
          dataSourceRef: 1
        sink:
          dataSourceRef: 2
        tables:
          - sourceTable: orders
        """;

    assertThatThrownBy(() -> codec.parse(yaml))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("keyColumns");
  }
}
