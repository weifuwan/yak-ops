package io.yak.ops.business.sync.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.yak.ops.business.sync.realtime.controller.v1.ComputeEnvironmentController;
import io.yak.ops.business.sync.realtime.controller.v1.RealtimeJobController;
import io.yak.ops.business.sync.realtime.domain.DefinitionDigest;
import io.yak.ops.business.sync.realtime.domain.DefinitionVersion;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState;
import io.yak.ops.business.sync.realtime.domain.RuntimeEnvironmentRef;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition;
import io.yak.ops.business.sync.realtime.domain.SyncDefinitionDigestCalculator;
import io.yak.ops.business.sync.realtime.domain.SyncExecution;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import io.yak.ops.business.sync.realtime.repository.ComputeEnvironmentStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobListQuery;
import io.yak.ops.business.sync.realtime.repository.RealtimeJobStore;
import io.yak.ops.business.sync.realtime.repository.RealtimeRuntimeIdentityStore;
import io.yak.ops.business.sync.realtime.service.ComputeEnvironmentService;
import io.yak.ops.business.sync.realtime.service.RealtimeJobLifecycleCoordinator;
import io.yak.ops.business.sync.realtime.service.RealtimeJobQueryService;
import io.yak.ops.business.sync.realtime.service.RealtimeJobService;
import io.yak.ops.business.sync.realtime.service.RealtimeObservabilityService;
import io.yak.ops.business.sync.realtime.service.RealtimeRuntimeResolver;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class RealtimeArchitectureTest {

  private static final String[] CORE_FORBIDDEN = {
    "org.springframework",
    "com.fasterxml.jackson",
    "com.baomidou",
    ".controller.",
    ".service.",
    ".repository.",
    ".dao.",
    ".engine.",
    "JdbcTemplate"
  };

  @Test
  void controllersDependOnApplicationBoundariesInsteadOfPersistenceOrEnginePorts() {
    assertFieldsAvoid(
        RealtimeJobController.class,
        ".repository.",
        ".dao.",
        "RealtimeEngineGateway",
        "JdbcTemplate");
    assertFieldsAvoid(
        ComputeEnvironmentController.class,
        ".repository.",
        ".dao.",
        "RealtimeEngineGateway",
        "JdbcTemplate");
  }

  @Test
  void servicesDoNotDependOnDaoMapperPoOrJdbcTemplate() {
    for (Class<?> type :
        new Class<?>[] {
          RealtimeJobService.class,
          RealtimeJobLifecycleCoordinator.class,
          RealtimeJobQueryService.class,
          RealtimeObservabilityService.class,
          RealtimeRuntimeResolver.class,
          ComputeEnvironmentService.class
        }) {
      assertFieldsAvoid(type, ".dao.", ".dao.mapper.", ".dao.model.", "JdbcTemplate");
    }
  }

  @Test
  void repositoryContractsDoNotExposeDaoOrControllerTypes() {
    for (Class<?> repository :
        new Class<?>[] {
          RealtimeJobStore.class,
          RealtimeJobListQuery.class,
          RealtimeRuntimeIdentityStore.class,
          ComputeEnvironmentStore.class
        }) {
      for (Method method : repository.getDeclaredMethods()) {
        assertTypeBoundary(method.getReturnType());
        for (Class<?> parameterType : method.getParameterTypes()) assertTypeBoundary(parameterType);
      }
    }
  }

  @Test
  void migratedExecutionContractCannotReintroduceTaskRuntimeSidePaths() {
    Set<String> storeMethods = methodNames(RealtimeJobStore.class);
    assertThat(storeMethods)
        .doesNotContain("desiredJobs", "hasOtherDesiredRunning", "markStarting");

    Set<String> serviceMethods = methodNames(RealtimeJobService.class);
    assertThat(serviceMethods)
        .contains("restartExecution", "applyPublishedVersion")
        .doesNotContain("restart");
  }

  @Test
  void coreDomainTypesStayFrameworkAndAdapterFree() {
    for (Class<?> root :
        new Class<?>[] {
          RealtimeJobState.class,
          SyncDefinition.class,
          RuntimeEnvironmentRef.class,
          DefinitionDigest.class,
          SyncDefinitionDigestCalculator.class,
          DefinitionVersion.class,
          SyncExecution.class,
          SyncExecutionStateMachine.class
        }) {
      assertCoreType(root);
      for (Class<?> nested : root.getDeclaredClasses()) assertCoreType(nested);
    }
  }

  private static void assertCoreType(Class<?> type) {
    assertAnnotationsAvoid(type, CORE_FORBIDDEN);

    for (Field field : type.getDeclaredFields()) {
      assertTypeAvoids(type, field.getName(), field.getGenericType(), CORE_FORBIDDEN);
      assertAnnotationsAvoid(field, CORE_FORBIDDEN);
    }
    for (Method method : type.getDeclaredMethods()) {
      assertTypeAvoids(type, method.getName() + " return", method.getGenericReturnType(), CORE_FORBIDDEN);
      for (Type parameter : method.getGenericParameterTypes()) {
        assertTypeAvoids(type, method.getName() + " parameter", parameter, CORE_FORBIDDEN);
      }
      assertAnnotationsAvoid(method, CORE_FORBIDDEN);
    }
    for (Constructor<?> constructor : type.getDeclaredConstructors()) {
      for (Type parameter : constructor.getGenericParameterTypes()) {
        assertTypeAvoids(type, "constructor parameter", parameter, CORE_FORBIDDEN);
      }
      assertAnnotationsAvoid(constructor, CORE_FORBIDDEN);
    }
    if (type.isRecord()) {
      for (RecordComponent component : type.getRecordComponents()) {
        assertTypeAvoids(type, component.getName(), component.getGenericType(), CORE_FORBIDDEN);
        assertAnnotationsAvoid(component, CORE_FORBIDDEN);
      }
    }
  }

  private static void assertAnnotationsAvoid(AnnotatedElement element, String... forbidden) {
    Arrays.stream(element.getDeclaredAnnotations())
        .forEach(
            annotation -> {
              String name = annotation.annotationType().getName();
              for (String value : forbidden) {
                assertThat(name)
                    .as("%s must not carry annotation from %s", element, value)
                    .doesNotContain(value);
              }
            });
  }

  private static void assertTypeAvoids(
      Class<?> owner, String member, Type type, String... forbidden) {
    String name = type.getTypeName();
    for (String value : forbidden) {
      assertThat(name)
          .as("%s.%s must not reference %s", owner.getSimpleName(), member, value)
          .doesNotContain(value);
    }
  }

  private static Set<String> methodNames(Class<?> type) {
    return Arrays.stream(type.getDeclaredMethods()).map(Method::getName).collect(Collectors.toSet());
  }

  private static void assertFieldsAvoid(Class<?> type, String... forbidden) {
    for (Field field : type.getDeclaredFields()) {
      String name = field.getType().getName();
      for (String value : forbidden) {
        assertThat(name)
            .as("%s.%s must not depend on %s", type.getSimpleName(), field.getName(), value)
            .doesNotContain(value);
      }
    }
  }

  private static void assertTypeBoundary(Class<?> type) {
    String name = type.getName();
    assertThat(name).doesNotContain(".dao.");
    assertThat(name).doesNotContain(".controller.");
    assertThat(name).doesNotContain("JdbcTemplate");
  }
}
