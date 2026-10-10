package io.yak.ops.business.datasync.impl;

import io.yak.ops.dao.entity.datasync.DataSyncTableRouteEntity;
import io.yak.ops.dao.entity.datasync.SyncDefinitionEntity;
import io.yak.ops.dao.repository.datasync.DataSyncTableRouteRepository;
import java.lang.reflect.Proxy;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 为直接构造 SyncDefinitionValidator 的 Contract Test 提供最小内存 Table Route Repository。
 */
final class DataSyncTestTableRouteRepository {

    private DataSyncTestTableRouteRepository() {}

    static DataSyncTableRouteRepository inject(Object service) throws Exception {
        return inject(service, List.of());
    }

    static DataSyncTableRouteRepository inject(Object service, SyncDefinitionEntity task) throws Exception {
        if (task == null) return inject(service);
        DataSyncTableRouteEntity route = new DataSyncTableRouteEntity();
        route.setId(task.getId());
        route.setWorkspaceId(task.getWorkspaceId());
        route.setTaskId(task.getId());
        route.setSourceDatabase(task.getSourceDatabase());
        route.setSourceSchema(task.getSourceSchema());
        route.setSourceTable(task.getSourceTable());
        route.setTargetDatabase(task.getTargetDatabase());
        route.setTargetSchema(task.getTargetSchema());
        route.setTargetTable(task.getTargetTable());
        route.setAutoCreateTable(Boolean.TRUE.equals(task.getAutoCreateTable()));
        route.setMappingConfig(task.getMappingConfig());
        route.setSortOrder(0);
        return inject(service, List.of(route));
    }

    static DataSyncTableRouteRepository inject(Object service, List<DataSyncTableRouteEntity> routes)
            throws Exception {
        DataSyncTableRouteRepository repository = create();
        if (routes != null) {
            routes.forEach(repository::add);
        }
        DataSyncTestServices.inject(service, "tableRouteRepository", repository);
        return repository;
    }

    static DataSyncTableRouteRepository create() {
        Map<String, DataSyncTableRouteEntity> routes = new LinkedHashMap<>();
        return (DataSyncTableRouteRepository) Proxy.newProxyInstance(
                DataSyncTableRouteRepository.class.getClassLoader(),
                new Class<?>[] {DataSyncTableRouteRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("add".equals(name)) {
                        DataSyncTableRouteEntity entity = (DataSyncTableRouteEntity) args[0];
                        routes.put(entity.getId(), entity);
                        return entity;
                    }
                    if ("queryByTask".equals(name)) {
                        String workspaceId = (String) args[0];
                        String taskId = (String) args[1];
                        return routes.values().stream()
                                .filter(route -> workspaceId.equals(route.getWorkspaceId()))
                                .filter(route -> taskId.equals(route.getTaskId()))
                                .sorted(Comparator.comparing(
                                                DataSyncTableRouteEntity::getSortOrder,
                                                Comparator.nullsLast(Integer::compareTo))
                                        .thenComparing(DataSyncTableRouteEntity::getId))
                                .toList();
                    }
                    if ("queryById".equals(name) && args.length == 2) {
                        String workspaceId = (String) args[0];
                        DataSyncTableRouteEntity entity = routes.get((String) args[1]);
                        return entity != null && workspaceId.equals(entity.getWorkspaceId())
                                ? Optional.of(entity)
                                : Optional.empty();
                    }
                    if ("update".equals(name) && args.length == 2) {
                        String workspaceId = (String) args[0];
                        DataSyncTableRouteEntity entity = (DataSyncTableRouteEntity) args[1];
                        if (!workspaceId.equals(entity.getWorkspaceId()) || !routes.containsKey(entity.getId())) {
                            return null;
                        }
                        routes.put(entity.getId(), entity);
                        return entity;
                    }
                    if ("deleteByTask".equals(name)) {
                        String workspaceId = (String) args[0];
                        String taskId = (String) args[1];
                        int before = routes.size();
                        routes.values().removeIf(route -> workspaceId.equals(route.getWorkspaceId())
                                && taskId.equals(route.getTaskId()));
                        return before - routes.size();
                    }
                    if ("queryById".equals(name) && args.length == 1) {
                        return Optional.ofNullable(routes.get((String) args[0]));
                    }
                    if ("deleteById".equals(name) && args.length == 1) {
                        return routes.remove((String) args[0]) == null ? 0 : 1;
                    }
                    if ("update".equals(name) && args.length == 1) {
                        DataSyncTableRouteEntity entity = (DataSyncTableRouteEntity) args[0];
                        if (!routes.containsKey(entity.getId())) return null;
                        routes.put(entity.getId(), entity);
                        return entity;
                    }
                    if ("queryList".equals(name)) return List.copyOf(routes.values());
                    if ("queryCount".equals(name)) return (long) routes.size();
                    if ("toString".equals(name)) return "DataSyncTestTableRouteRepository";
                    if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                    if ("equals".equals(name)) return proxy == args[0];
                    throw new UnsupportedOperationException(name);
                });
    }
}
