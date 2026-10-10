package io.yak.ops.business.datasync.impl;

import io.yak.ops.dao.entity.datasync.DataSyncTableExecutionEntity;
import io.yak.ops.dao.repository.datasync.DataSyncTableExecutionRepository;
import java.lang.reflect.Proxy;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 为直接构造 DataSyncInstanceServiceImpl 的 Contract Test 提供最小内存 Table Execution Repository。
 */
final class DataSyncTestTableExecutionRepository {

    private DataSyncTestTableExecutionRepository() {}

    static DataSyncTableExecutionRepository inject(Object service) throws Exception {
        DataSyncTableExecutionRepository repository = create();
        if (service instanceof DataSyncInstanceServiceImpl) {
            DataSyncTestServices.inject(service, "tableExecutionRepository", repository);
        }
        return repository;
    }

    static DataSyncTableExecutionRepository create() {
        Map<String, DataSyncTableExecutionEntity> executions = new LinkedHashMap<>();
        return (DataSyncTableExecutionRepository) Proxy.newProxyInstance(
                DataSyncTableExecutionRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncTableExecutionRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("add".equals(name)) {
                        DataSyncTableExecutionEntity entity = (DataSyncTableExecutionEntity) args[0];
                        executions.put(entity.getId(), entity);
                        return entity;
                    }
                    if ("finishUnfinished".equals(name)) {
                        return 0;
                    }
                    if ("queryByExecution".equals(name)) {
                        String workspaceId = (String) args[0];
                        String executionId = (String) args[1];
                        return executions.values().stream()
                                .filter(value -> workspaceId.equals(value.getWorkspaceId()))
                                .filter(value -> executionId.equals(value.getExecutionId()))
                                .sorted(Comparator.comparing(
                                                DataSyncTableExecutionEntity::getRouteOrder,
                                                Comparator.nullsLast(Integer::compareTo))
                                        .thenComparing(DataSyncTableExecutionEntity::getId))
                                .toList();
                    }
                    if ("queryById".equals(name) && args.length == 2) {
                        String workspaceId = (String) args[0];
                        DataSyncTableExecutionEntity entity = executions.get((String) args[1]);
                        return entity != null && workspaceId.equals(entity.getWorkspaceId())
                                ? Optional.of(entity)
                                : Optional.empty();
                    }
                    if ("queryById".equals(name) && args.length == 1) {
                        return Optional.ofNullable(executions.get((String) args[0]));
                    }
                    if ("deleteById".equals(name)) {
                        return executions.remove((String) args[0]) == null ? 0 : 1;
                    }
                    if ("update".equals(name)) {
                        DataSyncTableExecutionEntity entity =
                                (DataSyncTableExecutionEntity) args[args.length - 1];
                        executions.put(entity.getId(), entity);
                        return entity;
                    }
                    if ("queryList".equals(name)) return List.copyOf(executions.values());
                    if ("queryCount".equals(name)) return (long) executions.size();
                    if ("toString".equals(name)) return "DataSyncTestTableExecutionRepository";
                    if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                    if ("equals".equals(name)) return proxy == args[0];
                    throw new UnsupportedOperationException(name);
                });
    }
}
