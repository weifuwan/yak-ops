package io.yak.ops.business.datasync.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.yak.ops.business.datasync.exception.DataSyncException;
import io.yak.ops.common.bean.dto.datasync.DataSyncTableRouteDTO;
import io.yak.ops.dao.entity.datasync.DataSyncTableRouteEntity;
import io.yak.ops.dao.repository.datasync.DataSyncTableRouteRepository;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DataSyncTableRouteDefinitionContractTest {

    @Test
    void shouldPreserveRouteIdsAndAllowSwapWithoutSortOrderCollision() throws Exception {
        InMemoryRoutes repository = new InMemoryRoutes();
        repository.addSeed(route("route-users", "users", 0));
        repository.addSeed(route("route-orders", "orders", 1));
        DataSyncTableRouteDefinitionService service = service(repository);

        List<DataSyncTableRouteDTO> unchanged = List.of(
                request("route-users", "users"), request("route-orders", "orders"));
        assertFalse(service.changed("ws-1", "task-1", unchanged));

        List<DataSyncTableRouteDTO> reordered = List.of(
                request("route-orders", "orders"), request("route-users", "users"));
        assertTrue(service.changed("ws-1", "task-1", reordered));
        service.requireOwnedIds("ws-1", "task-1", reordered);
        service.reconcile("ws-1", "task-1", reordered, "operator");

        List<DataSyncTableRouteEntity> persisted = repository.ordered();
        assertEquals(List.of("route-orders", "route-users"),
                persisted.stream().map(DataSyncTableRouteEntity::getId).toList());
        assertEquals(List.of(0, 1),
                persisted.stream().map(DataSyncTableRouteEntity::getSortOrder).toList());
        assertFalse(service.changed("ws-1", "task-1", reordered));
    }

    @Test
    void shouldDeleteOnlyRemovedRouteAndAssignIdentityToNewRoute() throws Exception {
        InMemoryRoutes repository = new InMemoryRoutes();
        repository.addSeed(route("route-users", "users", 0));
        repository.addSeed(route("route-orders", "orders", 1));
        DataSyncTableRouteDefinitionService service = service(repository);

        List<DataSyncTableRouteDTO> requested = List.of(
                request("route-users", "users"), request(null, "products"));
        service.requireOwnedIds("ws-1", "task-1", requested);
        service.reconcile("ws-1", "task-1", requested, "operator");

        List<DataSyncTableRouteEntity> persisted = repository.ordered();
        assertEquals(2, persisted.size());
        assertEquals("route-users", persisted.get(0).getId());
        assertEquals("users", persisted.get(0).getSourceTable());
        assertEquals("products", persisted.get(1).getSourceTable());
        assertNotNull(persisted.get(1).getId());
        assertNotEquals("route-orders", persisted.get(1).getId());
        assertFalse(repository.contains("route-orders"));
    }

    @Test
    void shouldRejectForeignOrRepeatedRouteIdentity() throws Exception {
        InMemoryRoutes repository = new InMemoryRoutes();
        repository.addSeed(route("route-users", "users", 0));
        DataSyncTableRouteDefinitionService service = service(repository);

        assertThrows(DataSyncException.class, () -> service.requireOwnedIds(
                "ws-1", "task-1", List.of(request("foreign-route", "orders"))));
        assertThrows(DataSyncException.class, () -> service.requireOwnedIds(
                "ws-1", "task-1", List.of(
                        request("route-users", "users"), request("route-users", "orders"))));
        assertThrows(DataSyncException.class, () -> service.requireOwnedIds(
                "ws-1", null, List.of(request("route-users", "users"))));
        assertEquals(1, repository.ordered().size());
    }

    private DataSyncTableRouteDefinitionService service(InMemoryRoutes memory) throws Exception {
        DataSyncTableRouteDefinitionService service = new DataSyncTableRouteDefinitionService();
        Field field = DataSyncTableRouteDefinitionService.class.getDeclaredField("tableRouteRepository");
        field.setAccessible(true);
        field.set(service, memory.proxy());
        return service;
    }

    private DataSyncTableRouteDTO request(String id, String sourceTable) {
        DataSyncTableRouteDTO route = new DataSyncTableRouteDTO();
        route.setId(id);
        route.setSourceDatabase("app");
        route.setSourceTable(sourceTable);
        route.setTargetDatabase("warehouse");
        route.setTargetTable("ods_" + sourceTable);
        route.setAutoCreateTable(true);
        return route;
    }

    private DataSyncTableRouteEntity route(String id, String sourceTable, int order) {
        DataSyncTableRouteEntity entity = new DataSyncTableRouteEntity();
        entity.setId(id);
        entity.setWorkspaceId("ws-1");
        entity.setTaskId("task-1");
        entity.setSourceDatabase("app");
        entity.setSourceTable(sourceTable);
        entity.setTargetDatabase("warehouse");
        entity.setTargetTable("ods_" + sourceTable);
        entity.setAutoCreateTable(true);
        entity.setSortOrder(order);
        return entity;
    }

    private static DataSyncTableRouteEntity copy(DataSyncTableRouteEntity source) {
        DataSyncTableRouteEntity target = new DataSyncTableRouteEntity();
        target.setId(source.getId());
        target.setWorkspaceId(source.getWorkspaceId());
        target.setTaskId(source.getTaskId());
        target.setSourceDatabase(source.getSourceDatabase());
        target.setSourceSchema(source.getSourceSchema());
        target.setSourceTable(source.getSourceTable());
        target.setTargetDatabase(source.getTargetDatabase());
        target.setTargetSchema(source.getTargetSchema());
        target.setTargetTable(source.getTargetTable());
        target.setMappingConfig(source.getMappingConfig());
        target.setAutoCreateTable(source.getAutoCreateTable());
        target.setSortOrder(source.getSortOrder());
        return target;
    }

    private static final class InMemoryRoutes {
        private final Map<String, DataSyncTableRouteEntity> stored = new LinkedHashMap<>();

        void addSeed(DataSyncTableRouteEntity entity) {
            stored.put(entity.getId(), copy(entity));
        }

        boolean contains(String id) {
            return stored.containsKey(id);
        }

        List<DataSyncTableRouteEntity> ordered() {
            List<DataSyncTableRouteEntity> result = new ArrayList<>(stored.values());
            result.sort(Comparator.comparing(DataSyncTableRouteEntity::getSortOrder));
            return result.stream().map(DataSyncTableRouteDefinitionContractTest::copy).toList();
        }

        DataSyncTableRouteRepository proxy() {
            return (DataSyncTableRouteRepository) Proxy.newProxyInstance(
                    DataSyncTableRouteRepository.class.getClassLoader(),
                    new Class<?>[] {DataSyncTableRouteRepository.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("queryByTask".equals(name)) {
                            return ordered();
                        }
                        if ("add".equals(name)) {
                            DataSyncTableRouteEntity entity = (DataSyncTableRouteEntity) args[0];
                            verifyOrderUnique(entity);
                            stored.put(entity.getId(), copy(entity));
                            return entity;
                        }
                        if ("update".equals(name) && args.length == 2) {
                            DataSyncTableRouteEntity entity = (DataSyncTableRouteEntity) args[1];
                            if (!stored.containsKey(entity.getId())) return null;
                            verifyOrderUnique(entity);
                            stored.put(entity.getId(), copy(entity));
                            return entity;
                        }
                        if ("deleteById".equals(name) && args.length == 2) {
                            return stored.remove((String) args[1]) == null ? 0 : 1;
                        }
                        throw new UnsupportedOperationException(name);
                    });
        }

        private void verifyOrderUnique(DataSyncTableRouteEntity candidate) {
            for (DataSyncTableRouteEntity value : stored.values()) {
                if (!value.getId().equals(candidate.getId())
                        && value.getWorkspaceId().equals(candidate.getWorkspaceId())
                        && value.getTaskId().equals(candidate.getTaskId())
                        && value.getSortOrder().equals(candidate.getSortOrder())) {
                    throw new IllegalStateException("sortOrder unique constraint collision");
                }
            }
        }
    }
}
