package io.yak.ops.business.task.definition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.yak.ops.business.task.definition.impl.DefinitionServiceImpl;
import io.yak.ops.common.bean.dto.task.CreateDefinitionDTO;
import io.yak.ops.common.bean.dto.task.UpdateDefinitionDTO;
import io.yak.ops.common.context.WorkspaceContext;
import io.yak.ops.common.exception.BusinessException;
import io.yak.ops.dao.entity.task.DefinitionEntity;
import io.yak.ops.dao.entity.task.DefinitionVersionEntity;
import io.yak.ops.dao.repository.task.DefinitionRepository;
import io.yak.ops.dao.repository.task.DefinitionVersionRepository;
import io.yak.ops.configuration.ReadonlyConfig;
import io.yak.ops.plugin.task.api.TaskPlugin;
import io.yak.ops.plugin.task.api.TaskPluginRegistry;
import io.yak.ops.plugin.task.api.validation.TaskValidationContext;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Definition-only CRUD and frozen Workflow-addressable version contract. */
class DefinitionCrudContractTest {
    private final DefinitionServiceImpl service = new DefinitionServiceImpl();
    private final DefinitionRepository definitions = mock(DefinitionRepository.class);
    private final DefinitionVersionRepository versions = mock(DefinitionVersionRepository.class);
    private final TaskPluginRegistry registry = mock(TaskPluginRegistry.class);
    private final TaskValidationContext context = mock(TaskValidationContext.class);
    private final PublicationService publication = mock(PublicationService.class);
    private final Map<Integer, DefinitionVersionEntity> snapshots = new HashMap<>();
    private DefinitionEntity stored;


    DefinitionCrudContractTest() {
        inject("definitionRepository", definitions);
        inject("versionRepository", versions);
        inject("pluginRegistry", registry);
        inject("validationContext", context);
        inject("publicationService", publication);
        TaskPlugin plugin = mock(TaskPlugin.class);
        when(plugin.parameterSchemaVersion()).thenReturn(1);
        when(registry.get("DATA_SYNC")).thenReturn(plugin);
        when(publication.publish(anyString(), anyInt(), anyString())).thenAnswer(args -> {
            if (!java.util.Objects.equals(args.getArgument(1), stored.getCurrentVersion())) {
                throw new BusinessException(io.yak.ops.common.enums.common.CommonErrorCode.RESOURCE_DUPLICATION);
            }
            stored.setStatus(io.yak.ops.common.enums.task.DefinitionStatus.PUBLISHED);
            return stored;
        });
        when(plugin.parse(anyString()))
                 .thenAnswer(args -> ReadonlyConfig.fromMap(Map.of(
                        "sourceTable",
                        io.yak.ops.common.util.JSONUtils.readTree(args.getArgument(0))
                                .path("sourceTable")
                                .asText())));
        when(definitions.add(any())).thenAnswer(args -> {
            stored = args.getArgument(0);
            return stored;
        });
        when(definitions.queryByIdForUpdate(eq("ws"), anyString()))
                .thenAnswer(args -> Optional.ofNullable(stored));
        when(definitions.queryById(eq("ws"), anyString()))
                .thenAnswer(args -> Optional.ofNullable(stored));
        when(definitions.update(eq("ws"), any())).thenAnswer(args -> args.getArgument(1));
        when(versions.append(any())).thenAnswer(args -> {
            DefinitionVersionEntity version = args.getArgument(0);
            snapshots.put(version.getVersion(), version);
            return version;
        });
        when(versions.queryCurrent(eq("ws"), anyString(), anyInt()))
                .thenAnswer(args -> Optional.ofNullable(snapshots.get(args.getArgument(2))));
        when(versions.queryByDefinition(eq("ws"), anyString()))
                .thenAnswer(args -> snapshots.values().stream().toList());
    }

    @AfterEach
    void clearWorkspace() {
        WorkspaceContext.clear();
    }

    @Test
    void executableVersionsAreImmutableAndPublicationDoesNotBump() {
        WorkspaceContext.bind("ws");
        CreateDefinitionDTO create = new CreateDefinitionDTO();
        create.setName("orders");
        create.setTaskType("DATA_SYNC");
        create.setParameters(io.yak.ops.common.util.JSONUtils.toJson(java.util.Map.of("sourceTable", "orders")));
        assertEquals(1, service.create(create, "user-1").getDefinitionVersion());
        assertEquals(false, stored.isDebug());

        UpdateDefinitionDTO rename = new UpdateDefinitionDTO();
        rename.setName("orders renamed");
        rename.setDebug(true);
        assertEquals(1, service.update(stored.getId(), rename, "user-1").getDefinitionVersion());
        assertEquals(true, stored.isDebug());
        assertEquals(1, snapshots.size());

        UpdateDefinitionDTO change = new UpdateDefinitionDTO();
        change.setName("orders renamed");
        change.setParameters("{\"sourceTable\":\"orders_new\"}");
        assertEquals(2, service.update(stored.getId(), change, "user-1").getDefinitionVersion());
        assertEquals("orders", io.yak.ops.common.util.JSONUtils
                .readTree(snapshots.get(1).getParameters()).path("sourceTable").asText());
        assertEquals(2, snapshots.size());

        assertEquals(2, service.publish(stored.getId(), "user-1").getDefinitionVersion());
        assertThrows(BusinessException.class, () -> service.update(stored.getId(), change, "user-1"));
        assertEquals(2, service.unpublish(stored.getId(), "user-1").getDefinitionVersion());
    }

    @Test
    void equivalentJsonDoesNotCreateAnotherVersion() {
        WorkspaceContext.bind("ws");
        CreateDefinitionDTO create = new CreateDefinitionDTO();
        create.setName("orders");
        create.setTaskType("DATA_SYNC");
        create.setParameters("{\"sourceTable\":\"orders\"}");
        assertEquals(1, service.create(create, "user-1").getDefinitionVersion());

        UpdateDefinitionDTO formatted = new UpdateDefinitionDTO();
        formatted.setName("orders");
        formatted.setParameters("{ \"sourceTable\" : \"orders\" }");
        assertEquals(1, service.update(stored.getId(), formatted, "user-1").getDefinitionVersion());
        assertEquals(1, snapshots.size());

        formatted.setParameters("{\"sourceTable\":\"orders_new\"}");
        assertEquals(2, service.update(stored.getId(), formatted, "user-1").getDefinitionVersion());
        assertEquals(2, snapshots.size());
    }

    private void inject(String name, Object dependency) {
        try {
            Field field = DefinitionServiceImpl.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, dependency);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
