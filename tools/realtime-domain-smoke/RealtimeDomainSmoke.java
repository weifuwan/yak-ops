import io.yak.ops.business.sync.realtime.domain.DefinitionDigest;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.DesiredState;
import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.ObservedState;
import io.yak.ops.business.sync.realtime.domain.RuntimeEnvironmentRef;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.CheckpointPolicy;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.ExactTableSelector;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.ExecutionPolicy;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.NoRestart;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.ReplayKey;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.SchemaEvolutionPolicy;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.SinkEndpoint;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.SinkWritePolicy;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.SourceEndpoint;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.StartupPolicy;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.SyncPolicy;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.SyncRoute;
import io.yak.ops.business.sync.realtime.domain.SyncDefinition.TableTarget;
import io.yak.ops.business.sync.realtime.domain.SyncDefinitionDigestCalculator;
import io.yak.ops.business.sync.realtime.domain.SyncExecution;
import io.yak.ops.business.sync.realtime.domain.SyncExecution.EngineExecutionRef;
import io.yak.ops.business.sync.realtime.domain.SyncExecutionStateMachine;
import java.util.List;

/** Framework-free executable smoke test for the Realtime Sync core domain. */
public final class RealtimeDomainSmoke {

  private RealtimeDomainSmoke() {}

  public static void main(String[] args) {
    definitionInvariantsStayStrict();
    definitionDigestStaysSemantic();
    executionLifecycleStaysOneRunPerExecution();
    System.out.println("Realtime Domain Smoke: OK");
  }

  private static void definitionInvariantsStayStrict() {
    SyncDefinition valid = definition(routesInOrder());
    check(valid.routes().size() == 2, "valid SyncDefinition should keep both routes");

    expectThrows(
        IllegalArgumentException.class,
        () -> new ReplayKey(List.of("id", "id")),
        "ReplayKey must reject duplicate fields");

    expectThrows(
        IllegalArgumentException.class,
        () ->
            new SyncDefinition(
                new SourceEndpoint(1L),
                new SinkEndpoint(1L),
                routesInOrder(),
                policy(),
                executionPolicy()),
        "Source and Sink must not use the same DataSourceRef");
  }

  private static void definitionDigestStaysSemantic() {
    SyncDefinition first = definition(routesInOrder());
    SyncDefinition reordered =
        definition(
            List.of(
                new SyncRoute(
                    new ExactTableSelector("customers"),
                    new TableTarget("ods_customers"),
                    new ReplayKey(List.of("id"))),
                new SyncRoute(
                    new ExactTableSelector("orders"),
                    new TableTarget("ods_orders"),
                    new ReplayKey(List.of("id", "tenant_id")))));

    DefinitionDigest firstDigest =
        SyncDefinitionDigestCalculator.calculate(first, new RuntimeEnvironmentRef(3L));
    DefinitionDigest reorderedDigest =
        SyncDefinitionDigestCalculator.calculate(reordered, new RuntimeEnvironmentRef(3L));
    DefinitionDigest otherEnvironmentDigest =
        SyncDefinitionDigestCalculator.calculate(first, new RuntimeEnvironmentRef(4L));

    check(
        firstDigest.equals(reorderedDigest),
        "route/replay-key ordering without business semantics must not change DefinitionDigest");
    check(
        !firstDigest.equals(otherEnvironmentDigest),
        "RuntimeEnvironmentRef is part of DefinitionVersion semantics and must change digest");
  }

  private static void executionLifecycleStaysOneRunPerExecution() {
    SyncExecutionStateMachine stateMachine = new SyncExecutionStateMachine();

    SyncExecution running =
        execution(101L, DesiredState.RUNNING, ObservedState.RUNNING, "flink-job-101");
    expectThrows(
        IllegalStateException.class,
        () -> stateMachine.requireNewExecutionAllowed(running),
        "active execution must block a second execution");

    SyncExecution unknown =
        execution(102L, DesiredState.RUNNING, ObservedState.UNKNOWN, null);
    expectThrows(
        IllegalStateException.class,
        () -> stateMachine.requireNewExecutionAllowed(unknown),
        "UNKNOWN is active/uncertain and must block a second execution");

    SyncExecution stopped =
        execution(103L, DesiredState.STOPPED, ObservedState.STOPPED, "flink-job-103");
    stateMachine.requireNewExecutionAllowed(stopped);

    expectThrows(
        IllegalStateException.class,
        () -> stateMachine.requireTransition(ObservedState.STOPPED, ObservedState.STARTING),
        "STOPPED execution must be terminal");
    expectThrows(
        IllegalStateException.class,
        () -> stateMachine.requireTransition(ObservedState.FAILED, ObservedState.RUNNING),
        "FAILED execution must be terminal");
  }

  private static SyncExecution execution(
      long id, DesiredState desired, ObservedState observed, String externalExecutionId) {
    return new SyncExecution(
        id,
        7L,
        31L,
        desired,
        observed,
        new EngineExecutionRef("FLINK_CDC", externalExecutionId),
        observed == ObservedState.UNKNOWN,
        null);
  }

  private static SyncDefinition definition(List<SyncRoute> routes) {
    return new SyncDefinition(
        new SourceEndpoint(11L),
        new SinkEndpoint(22L),
        routes,
        policy(),
        executionPolicy());
  }

  private static List<SyncRoute> routesInOrder() {
    return List.of(
        new SyncRoute(
            new ExactTableSelector("orders"),
            new TableTarget("ods_orders"),
            new ReplayKey(List.of("tenant_id", "id"))),
        new SyncRoute(
            new ExactTableSelector("customers"),
            new TableTarget("ods_customers"),
            new ReplayKey(List.of("id"))));
  }

  private static SyncPolicy policy() {
    return new SyncPolicy(StartupPolicy.INITIAL_AND_CONTINUOUS, SchemaEvolutionPolicy.EVOLVE);
  }

  private static ExecutionPolicy executionPolicy() {
    return new ExecutionPolicy(
        1,
        new CheckpointPolicy(60_000L),
        new NoRestart(),
        new SinkWritePolicy(3, 1_000, 2_000L, 16_777_216L));
  }

  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  private static void expectThrows(
      Class<? extends Throwable> expected, Runnable operation, String message) {
    try {
      operation.run();
    } catch (Throwable actual) {
      if (expected.isInstance(actual)) return;
      throw new AssertionError(
          message + "; expected " + expected.getSimpleName() + " but got " + actual, actual);
    }
    throw new AssertionError(message + "; expected " + expected.getSimpleName());
  }
}
