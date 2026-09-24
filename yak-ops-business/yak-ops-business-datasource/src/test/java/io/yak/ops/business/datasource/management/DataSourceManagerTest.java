package io.yak.ops.business.datasource.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.yak.ops.business.datasource.connection.DataSourceConnectionResolver;
import io.yak.ops.business.datasource.domain.ConnectionProfile;
import io.yak.ops.business.datasource.domain.DataSourceChangedEvent;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.common.enums.datasource.DataSourceConnStatus;
import io.yak.ops.common.enums.datasource.DataSourceDbType;
import io.yak.ops.common.enums.datasource.DataSourceEnvironment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class DataSourceManagerTest {

  @Mock private DataSourceRepository repository;
  @Mock private DataSourceReader reader;
  @Mock private DataSourceConnectionResolver connectionResolver;
  @Mock private ApplicationEventPublisher eventPublisher;

  @Test
  void createBuildsAggregateFromNormalizedConnectionProfile() {
    DataSourceConfigurationCommand command =
        new DataSourceConfigurationCommand(
            "orders-db",
            DataSourceDbType.MYSQL,
            DataSourceEnvironment.PROD,
            "orders",
            "{\"host\":\"127.0.0.1\"}");
    ConnectionProfile profile =
        new ConnectionProfile(
            "jdbc:mysql://127.0.0.1/orders",
            "{\"host\":\"127.0.0.1\"}",
            "{\"host\":\"127.0.0.1\"}");
    when(connectionResolver.normalize(DataSourceDbType.MYSQL, command.connectionJson()))
        .thenReturn(profile);
    when(repository.insert(any(DataSourceDefinition.class))).thenReturn(true);

    assertThat(manager().create(command)).isTrue();

    ArgumentCaptor<DataSourceDefinition> captor =
        ArgumentCaptor.forClass(DataSourceDefinition.class);
    verify(repository).insert(captor.capture());
    assertThat(captor.getValue().getName()).isEqualTo("orders-db");
    assertThat(captor.getValue().getConnStatus()).isEqualTo(DataSourceConnStatus.UNKNOWN);
  }

  @Test
  void updatePublishesDatasourceChangedEvent() {
    DataSourceConfigurationCommand command =
        new DataSourceConfigurationCommand(
            "orders-db-v2",
            DataSourceDbType.MYSQL,
            DataSourceEnvironment.TEST,
            "updated",
            "{\"host\":\"db.internal\",\"password\":\"new-super-secret\"}");
    ConnectionProfile stored =
        new ConnectionProfile(
            "jdbc:mysql://127.0.0.1/orders",
            "{\"host\":\"127.0.0.1\",\"password\":\"old-secret\"}",
            "{\"host\":\"127.0.0.1\",\"password\":\"old-secret\"}");
    ConnectionProfile merged =
        new ConnectionProfile(
            "jdbc:mysql://db.internal/orders",
            "{\"host\":\"db.internal\",\"password\":\"new-super-secret\"}",
            "{\"host\":\"db.internal\",\"password\":\"new-super-secret\"}");
    DataSourceDefinition existing =
        DataSourceDefinition.create(
            "orders-db",
            DataSourceDbType.MYSQL,
            stored,
            DataSourceEnvironment.PROD,
            null);
    when(reader.require(42L)).thenReturn(existing);
    when(connectionResolver.mergeStoredSecrets(existing, command.connectionJson()))
        .thenReturn(merged);
    when(repository.update(existing)).thenReturn(true);

    assertThat(manager().update(42L, command)).isTrue();

    ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue()).isEqualTo(new DataSourceChangedEvent(42L));
  }

  @Test
  void failedCreateRethrowsBusinessError() {
    DataSourceConfigurationCommand command =
        new DataSourceConfigurationCommand(
            "orders-db",
            DataSourceDbType.MYSQL,
            DataSourceEnvironment.PROD,
            null,
            "{\"host\":\"127.0.0.1\"}");
    ConnectionProfile profile =
        new ConnectionProfile(
            "jdbc:mysql://127.0.0.1/orders",
            "{\"host\":\"127.0.0.1\"}",
            "{\"host\":\"127.0.0.1\"}");
    when(connectionResolver.normalize(DataSourceDbType.MYSQL, command.connectionJson()))
        .thenReturn(profile);
    when(repository.insert(any(DataSourceDefinition.class))).thenReturn(false);

    assertThatThrownBy(() -> manager().create(command)).isInstanceOf(RuntimeException.class);
  }

  private DataSourceManager manager() {
    return new DataSourceManager(repository, reader, connectionResolver, eventPublisher);
  }
}
