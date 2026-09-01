package io.yak.ops.business.quality.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.yak.ops.business.quality.config.ConditionalOnQualityEnabled;
import io.yak.ops.business.quality.domain.QualityDomain.Monitor;
import io.yak.ops.business.quality.domain.QualityDomain.Rule;
import io.yak.ops.business.quality.domain.QualityTaskRevision;
import io.yak.ops.business.quality.domain.execution.QualityExecutionDefinition;
import io.yak.ops.business.quality.execution.QualityExecutionPlanFactory;
import io.yak.ops.business.quality.repository.QualityTaskRevisionRepository;
import io.yak.ops.business.taskcatalog.service.TaskCatalogService;
import io.yak.ops.spi.task.model.TaskAssetSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Publishes enabled quality monitors as versioned workflow task assets. */
@Component
@ConditionalOnQualityEnabled
public class QualityTaskPublisher {
  static final String TASK_TYPE = "QUALITY";

  private final QualityTaskRevisionRepository revisionRepository;
  private final QualityExecutionPlanFactory planFactory;
  private final TaskCatalogService taskCatalogService;
  private final ObjectMapper objectMapper;

  public QualityTaskPublisher(
      QualityTaskRevisionRepository revisionRepository,
      QualityExecutionPlanFactory planFactory,
      TaskCatalogService taskCatalogService,
      ObjectMapper objectMapper) {
    this.revisionRepository = revisionRepository;
    this.planFactory = planFactory;
    this.taskCatalogService = taskCatalogService;
    this.objectMapper = objectMapper;
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public void sync(Monitor monitor) {
    if (!eligible(monitor)) {
      offline(monitor.id());
      return;
    }

    QualityExecutionDefinition definition = planFactory.freeze(monitor);
    String definitionJson = write(definition);
    String checksum = checksum(definitionJson);
    QualityTaskRevision latest = revisionRepository.findLatest(monitor.id()).orElse(null);
    QualityTaskRevision revision = latest != null && checksum.equals(latest.checksum())
        ? latest
        : revisionRepository.insert(
            monitor.id(),
            latest == null ? 1 : latest.revisionNo() + 1,
            monitor.name(),
            definitionJson,
            checksum);

    taskCatalogService.publish(
        TaskAssetSource.DATA_QUALITY,
        String.valueOf(monitor.id()),
        definition.projectId(),
        monitor.name(),
        TASK_TYPE,
        revision.id(),
        revision.revisionNo());
  }

  @Transactional(transactionManager = "yakBusinessTransactionManager")
  public void offline(long monitorId) {
    taskCatalogService.offlineSource(TaskAssetSource.DATA_QUALITY, String.valueOf(monitorId));
  }

  static boolean eligible(Monitor monitor) {
    return monitor != null
        && monitor.enabled()
        && monitor.rules().stream().anyMatch(Rule::enabled);
  }

  private String write(QualityExecutionDefinition definition) {
    try {
      return objectMapper.writeValueAsString(definition);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("质量任务执行定义序列化失败", exception);
    }
  }

  private String checksum(String value) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
    }
  }
}
