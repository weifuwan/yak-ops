package io.yak.ops.business.datasync.execution.lifecycle;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * 为单表 Execution 保留进程内启动/取消令牌，使取消能够覆盖运行计划已创建但 YakFlow 尚未启动的窗口。
 *
 * @author weifuwan
 * @since 2026-09-27
 */
@Component
public class DataSyncExecutionRegistry {

    private final ConcurrentMap<String, DataSyncExecutionControl> controls = new ConcurrentHashMap<>();

    public DataSyncExecutionControl reserve(String instanceId) {
        DataSyncExecutionControl control = new DataSyncExecutionControl();
        if (controls.putIfAbsent(instanceId, control) != null) {
            throw new IllegalStateException("data sync execution already registered: " + instanceId);
        }
        return control;
    }

    public boolean cancel(String instanceId) {
        DataSyncExecutionControl control = controls.get(instanceId);
        if (control == null) return false;
        control.cancel();
        return true;
    }

    public void remove(String instanceId, DataSyncExecutionControl control) {
        controls.remove(instanceId, control);
    }
}
