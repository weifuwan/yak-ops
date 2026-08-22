package io.yak.ops.business.sync.realtime.controller.v1.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class RealtimeJobRequests {
  private RealtimeJobRequests() {}

  public record CreateRequest(
      @NotBlank @Size(max = 200) String name,
      @Size(max = 1000) String description,
      @NotNull Long runtimeEnvironmentId) {}

  public record SaveRequest(
      @NotBlank @Size(max = 200) String name,
      @Size(max = 1000) String description,
      @NotNull Long runtimeEnvironmentId,
      @Valid PipelineSpec spec) {}

  public record YamlRequest(@NotBlank @Size(max = 65_536) String yaml) {}

  public record YamlRenderRequest(@Valid @NotNull PipelineSpec spec) {}

  public record PipelineSpec(
      @NotNull Long sourceDataSourceRef,
      @NotNull Long sinkDataSourceRef,
      @NotEmpty List<@Valid TableRoute> tables,
      @Pattern(regexp = "initial|latest-offset") String startupMode,
      @NotNull SchemaEvolution schemaEvolution,
      @Min(1) @Max(256) int parallelism,
      @Min(10_000) long checkpointIntervalMs,
      @Valid @NotNull RestartPolicy restart,
      @Valid @NotNull SinkTuning sink) {}

  public enum SchemaEvolution { EVOLVE, IGNORE, FAIL }
  public enum MatchMode { EXACT, REGEX }

  public record TableRoute(
      @NotBlank String sourceTable,
      @NotBlank String sinkTable,
      @NotNull MatchMode matchMode,
      @NotEmpty List<@NotBlank String> keyColumns) {}

  public record RestartPolicy(
      @Pattern(regexp = "fixed-delay|failure-rate|none") String strategy,
      @Min(0) int attempts,
      @Min(0) long delayMs) {}

  public record SinkTuning(
      @Min(0) int maxRetries,
      @Min(1) int batchSize,
      @Min(1) long flushIntervalMs,
      @Min(1) long maxBatchBytes,
      @Min(1) int statementCacheSize,
      boolean strictReplaySafety) {}
}
