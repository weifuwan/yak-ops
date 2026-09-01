package io.yak.ops.business.quality.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.yak.ops.business.quality.domain.QualityTaskRevision;
import io.yak.ops.business.quality.repository.QualityTaskRevisionRepository;
import io.yak.ops.business.taskcatalog.spi.TaskSourceRevision;
import io.yak.ops.spi.task.model.TaskAssetSource;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class QualityTaskRevisionProviderTest {

  @Test
  void resolvesPinnedQualityRevisionWithoutReadingCurrentMonitor() {
    QualityTaskRevisionRepository repository = Mockito.mock(QualityTaskRevisionRepository.class);
    QualityTaskRevision revision = new QualityTaskRevision(
        101L,
        7L,
        42L,
        3,
        "customers-quality",
        "{\"projectId\":7}",
        "digest",
        LocalDateTime.now());
    when(repository.find(42L, 101L)).thenReturn(Optional.of(revision));
    QualityTaskRevisionProvider provider = new QualityTaskRevisionProvider(repository);

    TaskSourceRevision resolved = provider.resolve("42", 101L).orElseThrow();

    assertThat(provider.source()).isEqualTo(TaskAssetSource.DATA_QUALITY);
    assertThat(resolved.revisionId()).isEqualTo(101L);
    assertThat(resolved.revisionNo()).isEqualTo(3);
    assertThat(resolved.sourceProjectId()).isEqualTo(7L);
    assertThat(resolved.checksum()).isEqualTo("digest");
    assertThat(resolved.definition().taskType()).isEqualTo("QUALITY");
    assertThat(resolved.definition().configJson()).isEqualTo("{\"projectId\":7}");
  }

  @Test
  void rejectsMalformedQualitySourceReference() {
    QualityTaskRevisionProvider provider = new QualityTaskRevisionProvider(
        Mockito.mock(QualityTaskRevisionRepository.class));

    assertThatThrownBy(() -> provider.resolve("monitor-42", 101L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sourceRef");
  }
}
