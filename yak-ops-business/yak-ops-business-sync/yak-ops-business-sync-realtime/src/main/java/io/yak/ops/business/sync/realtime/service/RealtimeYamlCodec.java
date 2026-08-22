package io.yak.ops.business.sync.realtime.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpec;
import io.yak.ops.business.sync.realtime.domain.CdcPipelineSpecValidator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Converts the password-free Yak realtime YAML format to and from the canonical pipeline spec. */
@Component
public class RealtimeYamlCodec {

  public static final int FORMAT_VERSION = 1;
  public static final int MAX_YAML_LENGTH = 65_536;

  private static final CdcPipelineSpec DEFAULT_SPEC =
      new CdcPipelineSpec(
          0L,
          0L,
          List.of(),
          "initial",
          CdcPipelineSpec.SchemaEvolution.EVOLVE,
          1,
          60_000,
          new CdcPipelineSpec.RestartPolicy("fixed-delay", 3, 10_000),
          new CdcPipelineSpec.SinkTuning(3, 1_000, 2_000, 16_777_216, 128, true));

  private final ObjectMapper yaml;
  private final Validator beanValidator;
  private final CdcPipelineSpecValidator specValidator;

  public RealtimeYamlCodec(Validator beanValidator, CdcPipelineSpecValidator specValidator) {
    YAMLFactory factory = new YAMLFactory();
    factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER);
    this.yaml = new ObjectMapper(factory);
    this.yaml.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    this.yaml.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    this.beanValidator = beanValidator;
    this.specValidator = specValidator;
  }

  public CdcPipelineSpec parse(String source) {
    if (!StringUtils.hasText(source)) {
      throw new IllegalArgumentException("Yak Realtime YAML 不能为空");
    }
    if (source.length() > MAX_YAML_LENGTH) {
      throw new IllegalArgumentException("Yak Realtime YAML 不能超过 " + MAX_YAML_LENGTH + " 个字符");
    }

    try {
      YamlDocument document = yaml.readValue(source, YamlDocument.class);
      if (document == null) {
        throw new IllegalArgumentException("Yak Realtime YAML 不能为空");
      }
      int version = document.version() == null ? FORMAT_VERSION : document.version();
      if (version != FORMAT_VERSION) {
        throw new IllegalArgumentException("不支持的 Yak Realtime YAML version：" + version);
      }

      CdcPipelineSpec spec = toSpec(document);
      validate(spec);
      return spec;
    } catch (IllegalArgumentException exception) {
      throw exception;
    } catch (JsonProcessingException exception) {
      String location =
          exception.getLocation() == null
              ? ""
              : "（第 "
                  + exception.getLocation().getLineNr()
                  + " 行，第 "
                  + exception.getLocation().getColumnNr()
                  + " 列）";
      throw new IllegalArgumentException("Yak Realtime YAML 解析失败" + location + "：" + exception.getOriginalMessage());
    }
  }

  public String render(CdcPipelineSpec spec) {
    validate(spec);
    try {
      return yaml.writeValueAsString(fromSpec(spec));
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Yak Realtime YAML 生成失败：" + exception.getOriginalMessage());
    }
  }

  private CdcPipelineSpec toSpec(YamlDocument document) {
    Endpoint source = document.source();
    Endpoint sink = document.sink();
    SyncOptions sync = document.sync();
    RuntimeOptions runtime = document.runtime();
    RestartOptions restart = runtime == null ? null : runtime.restart();
    SinkOptions sinkOptions = runtime == null ? null : runtime.sink();

    List<CdcPipelineSpec.TableRoute> tables =
        document.tables() == null
            ? List.of()
            : document.tables().stream().map(this::toRoute).toList();

    return new CdcPipelineSpec(
        source == null ? null : source.dataSourceRef(),
        sink == null ? null : sink.dataSourceRef(),
        tables,
        textOrDefault(sync == null ? null : sync.startupMode(), DEFAULT_SPEC.startupMode()),
        schemaEvolution(sync == null ? null : sync.schemaEvolution()),
        intOrDefault(runtime == null ? null : runtime.parallelism(), DEFAULT_SPEC.parallelism()),
        longOrDefault(
            runtime == null ? null : runtime.checkpointIntervalMs(),
            DEFAULT_SPEC.checkpointIntervalMs()),
        new CdcPipelineSpec.RestartPolicy(
            textOrDefault(
                restart == null ? null : restart.strategy(), DEFAULT_SPEC.restart().strategy()),
            intOrDefault(restart == null ? null : restart.attempts(), DEFAULT_SPEC.restart().attempts()),
            longOrDefault(restart == null ? null : restart.delayMs(), DEFAULT_SPEC.restart().delayMs())),
        new CdcPipelineSpec.SinkTuning(
            intOrDefault(
                sinkOptions == null ? null : sinkOptions.maxRetries(),
                DEFAULT_SPEC.sink().maxRetries()),
            intOrDefault(
                sinkOptions == null ? null : sinkOptions.batchSize(), DEFAULT_SPEC.sink().batchSize()),
            longOrDefault(
                sinkOptions == null ? null : sinkOptions.flushIntervalMs(),
                DEFAULT_SPEC.sink().flushIntervalMs()),
            longOrDefault(
                sinkOptions == null ? null : sinkOptions.maxBatchBytes(),
                DEFAULT_SPEC.sink().maxBatchBytes()),
            intOrDefault(
                sinkOptions == null ? null : sinkOptions.statementCacheSize(),
                DEFAULT_SPEC.sink().statementCacheSize()),
            sinkOptions == null || sinkOptions.strictReplaySafety() == null
                ? DEFAULT_SPEC.sink().strictReplaySafety()
                : sinkOptions.strictReplaySafety()));
  }

  private CdcPipelineSpec.TableRoute toRoute(TableMapping mapping) {
    if (mapping == null) {
      throw new IllegalArgumentException("tables 中不能包含空项");
    }
    String sourceTable = mapping.sourceTable();
    String sinkTable =
        StringUtils.hasText(mapping.sinkTable()) ? mapping.sinkTable().trim() : sourceTable;
    CdcPipelineSpec.MatchMode matchMode = matchMode(mapping.matchMode());
    List<String> keyColumns =
        mapping.keyColumns() == null
            ? List.of()
            : mapping.keyColumns().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    return new CdcPipelineSpec.TableRoute(sourceTable, sinkTable, matchMode, keyColumns);
  }

  private YamlDocument fromSpec(CdcPipelineSpec spec) {
    return new YamlDocument(
        FORMAT_VERSION,
        new Endpoint(spec.sourceDataSourceRef()),
        new Endpoint(spec.sinkDataSourceRef()),
        spec.tables().stream()
            .map(
                route ->
                    new TableMapping(
                        route.sourceTable(),
                        route.sinkTable(),
                        route.matchMode().name(),
                        route.keyColumns()))
            .toList(),
        new SyncOptions(spec.startupMode(), spec.schemaEvolution().name()),
        new RuntimeOptions(
            spec.parallelism(),
            spec.checkpointIntervalMs(),
            new RestartOptions(
                spec.restart().strategy(), spec.restart().attempts(), spec.restart().delayMs()),
            new SinkOptions(
                spec.sink().maxRetries(),
                spec.sink().batchSize(),
                spec.sink().flushIntervalMs(),
                spec.sink().maxBatchBytes(),
                spec.sink().statementCacheSize(),
                spec.sink().strictReplaySafety())));
  }

  private void validate(CdcPipelineSpec spec) {
    List<String> violations =
        beanValidator.validate(spec).stream()
            .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
            .map(this::violationMessage)
            .toList();
    if (!violations.isEmpty()) {
      throw new IllegalArgumentException("Yak Realtime YAML 配置无效：" + String.join("；", violations));
    }
    specValidator.validate(spec);
  }

  private String violationMessage(ConstraintViolation<CdcPipelineSpec> violation) {
    String path = violation.getPropertyPath().toString();
    return (path.isBlank() ? "配置" : path) + " " + violation.getMessage();
  }

  private CdcPipelineSpec.SchemaEvolution schemaEvolution(String value) {
    String normalized =
        textOrDefault(value, DEFAULT_SPEC.schemaEvolution().name()).toUpperCase(Locale.ROOT);
    try {
      return CdcPipelineSpec.SchemaEvolution.valueOf(normalized);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("不支持的 schemaEvolution：" + value);
    }
  }

  private CdcPipelineSpec.MatchMode matchMode(String value) {
    String normalized = textOrDefault(value, "EXACT").toUpperCase(Locale.ROOT);
    try {
      return CdcPipelineSpec.MatchMode.valueOf(normalized);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("不支持的 matchMode：" + value);
    }
  }

  private String textOrDefault(String value, String defaultValue) {
    return StringUtils.hasText(value) ? value.trim() : defaultValue;
  }

  private int intOrDefault(Integer value, int defaultValue) {
    return value == null ? defaultValue : value;
  }

  private long longOrDefault(Long value, long defaultValue) {
    return value == null ? defaultValue : value;
  }

  public record YamlDocument(
      Integer version,
      Endpoint source,
      Endpoint sink,
      List<TableMapping> tables,
      SyncOptions sync,
      RuntimeOptions runtime) {}

  public record Endpoint(Long dataSourceRef) {}

  public record TableMapping(
      String sourceTable, String sinkTable, String matchMode, List<String> keyColumns) {}

  public record SyncOptions(String startupMode, String schemaEvolution) {}

  public record RuntimeOptions(
      Integer parallelism, Long checkpointIntervalMs, RestartOptions restart, SinkOptions sink) {}

  public record RestartOptions(String strategy, Integer attempts, Long delayMs) {}

  public record SinkOptions(
      Integer maxRetries,
      Integer batchSize,
      Long flushIntervalMs,
      Long maxBatchBytes,
      Integer statementCacheSize,
      Boolean strictReplaySafety) {}
}
