package io.yak.ops.plugin.task.datasync;

/** Keys for execution-only options; these values are not part of the persisted pipeline definition. */
public final class DataSyncRuntimeOptions {

    public static final String RESTORE_PATH = "__yak_data_sync_restore_path";

    private DataSyncRuntimeOptions() {
    }
}
