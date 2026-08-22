package io.yak.ops.business.sync.realtime.domain;

import io.yak.ops.business.sync.realtime.domain.RealtimeJobState.ObservedState;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/** State machine for one immutable-identity SyncExecution. */
public class SyncExecutionStateMachine {

  private final Map<ObservedState, EnumSet<ObservedState>> transitions =
      new EnumMap<>(ObservedState.class);

  public SyncExecutionStateMachine() {
    allow(
        ObservedState.STARTING,
        ObservedState.RUNNING,
        ObservedState.STOPPING,
        ObservedState.FAILED,
        ObservedState.UNKNOWN,
        ObservedState.CONFLICT);
    allow(
        ObservedState.RUNNING,
        ObservedState.STOPPING,
        ObservedState.FAILED,
        ObservedState.UNKNOWN,
        ObservedState.CONFLICT);
    allow(
        ObservedState.STOPPING,
        ObservedState.STOPPED,
        ObservedState.FAILED,
        ObservedState.UNKNOWN);
    allow(
        ObservedState.UNKNOWN,
        ObservedState.RUNNING,
        ObservedState.STOPPING,
        ObservedState.STOPPED,
        ObservedState.FAILED,
        ObservedState.CONFLICT);
    allow(
        ObservedState.CONFLICT,
        ObservedState.RUNNING,
        ObservedState.STOPPING,
        ObservedState.STOPPED,
        ObservedState.FAILED,
        ObservedState.UNKNOWN);
    // STOPPED / FAILED intentionally have no outgoing transitions. A later run is a new execution.
  }

  public void requireTransition(SyncExecution execution, String targetValue) {
    if (execution == null) throw new IllegalArgumentException("SyncExecution 不能为空");
    ObservedState target = ObservedState.valueOf(targetValue);
    requireTransition(execution.observedState(), target);
  }

  public void requireTransition(ObservedState from, ObservedState target) {
    if (from == target) return;
    if (!transitions.getOrDefault(from, EnumSet.noneOf(ObservedState.class)).contains(target)) {
      throw new IllegalStateException("非法 SyncExecution 状态迁移：" + from + " -> " + target);
    }
  }

  /** New execution creation is allowed only when no execution exists or the latest one is terminal. */
  public void requireNewExecutionAllowed(SyncExecution latest) {
    if (latest != null && latest.activeOrUncertain()) {
      throw new IllegalStateException("任务已有启动中、运行中、停止中或状态不确定的 Execution，请勿重复启动");
    }
  }

  /** Destructive metadata mutation (currently delete) requires a terminal execution. */
  public void requireDefinitionMutable(SyncExecution latest) {
    if (latest != null && latest.activeOrUncertain()) {
      throw new IllegalStateException("任务运行态未稳定，只有已停止或明确失败的任务才能执行该操作");
    }
  }

  public boolean activeOrUncertain(SyncExecution execution) {
    return execution != null && execution.activeOrUncertain();
  }

  private void allow(ObservedState from, ObservedState... targets) {
    transitions.put(from, EnumSet.copyOf(Arrays.asList(targets)));
  }
}
