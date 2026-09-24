package io.yak.ops.business.datasource.management;

import io.yak.ops.business.datasource.config.ConditionalOnDataSourceEnabled;
import io.yak.ops.business.datasource.connection.DataSourceConnectionResolver;
import io.yak.ops.business.datasource.domain.ConnectionProfile;
import io.yak.ops.business.datasource.domain.DataSourceChangedEvent;
import io.yak.ops.business.datasource.domain.DataSourceDefinition;
import io.yak.ops.business.datasource.exception.DataSourceException;
import io.yak.ops.business.datasource.query.DataSourceReader;
import io.yak.ops.business.datasource.repository.DataSourceRepository;
import io.yak.ops.common.enums.datasource.DataSourceErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Owns datasource aggregate lifecycle commands. */
@Component
@ConditionalOnDataSourceEnabled
@RequiredArgsConstructor
public class DataSourceManager {

  private final DataSourceRepository repository;
  private final DataSourceReader reader;
  private final DataSourceConnectionResolver connectionResolver;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(
      transactionManager = "opsDataSourceTransactionManager",
      rollbackFor = Exception.class)
  public boolean create(DataSourceConfigurationCommand command) {
    ensureNameAvailable(command.name(), null);
    ConnectionProfile connectionProfile =
        connectionResolver.normalize(command.dbType(), command.connectionJson());
    DataSourceDefinition definition =
        DataSourceDefinition.create(
            command.name(),
            command.dbType(),
            connectionProfile,
            command.environment(),
            command.remark());
    if (!repository.insert(definition)) {
      throw new DataSourceException(DataSourceErrorCode.CREATE_FAILED);
    }
    return true;
  }

  @Transactional(
      transactionManager = "opsDataSourceTransactionManager",
      rollbackFor = Exception.class)
  public boolean update(Long id, DataSourceConfigurationCommand command) {
    DataSourceDefinition existing = reader.require(id);
    ensureNameAvailable(command.name(), id);
    assertTypeUnchanged(existing, command);
    ConnectionProfile connectionProfile =
        connectionResolver.mergeStoredSecrets(existing, command.connectionJson());
    existing.updateConfiguration(
        command.name(),
        command.dbType(),
        connectionProfile,
        command.environment(),
        command.remark());
    if (!repository.update(existing)) {
      throw new DataSourceException(DataSourceErrorCode.UPDATE_FAILED);
    }
    eventPublisher.publishEvent(new DataSourceChangedEvent(id));
    return true;
  }

  @Transactional(
      transactionManager = "opsDataSourceTransactionManager",
      rollbackFor = Exception.class)
  public boolean delete(Long id) {
    DataSourceDefinition existing = reader.require(id);
    if (!repository.delete(existing.getId())) {
      throw new DataSourceException(DataSourceErrorCode.DELETE_FAILED);
    }
    eventPublisher.publishEvent(new DataSourceChangedEvent(existing.getId()));
    return true;
  }

  private void ensureNameAvailable(String name, Long excludeId) {
    if (repository.existsByName(name, excludeId)) {
      throw new DataSourceException(DataSourceErrorCode.DUPLICATE_NAME);
    }
  }

  private void assertTypeUnchanged(
      DataSourceDefinition existing,
      DataSourceConfigurationCommand command) {
    try {
      existing.assertTypeUnchanged(command.dbType());
    } catch (IllegalArgumentException exception) {
      throw new DataSourceException(
          DataSourceErrorCode.INVALID_DB_TYPE,
          exception.getMessage(),
          exception);
    }
  }
}
