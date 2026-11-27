package io.yak.ops.api.enums;

/** Actions that can be performed on a data synchronization task instance. */
public enum DataSyncInstanceAction {
    STOP,
    RESUME,
    RECOVER_FAILURE,
    RERUN
}
