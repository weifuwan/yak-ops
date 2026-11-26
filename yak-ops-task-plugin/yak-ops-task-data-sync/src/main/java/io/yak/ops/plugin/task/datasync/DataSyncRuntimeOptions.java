package io.yak.ops.plugin.task.datasync;

/** Keys for execution-only options; these values are not part of the persisted pipeline definition. */
public final class DataSyncRuntimeOptions {

    public static final String RESTORE_PATH = "__yak_data_sync_restore_path";

    public static final String RECOVERY_POINT_ID = "__yak_data_sync_recovery_point_id";

    private DataSyncRuntimeOptions() {
    }
}
