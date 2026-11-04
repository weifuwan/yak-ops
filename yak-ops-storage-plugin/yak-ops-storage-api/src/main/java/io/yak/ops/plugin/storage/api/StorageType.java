package io.yak.ops.plugin.storage.api;

import java.util.Optional;

public enum StorageType {

    LOCAL,
    HDFS,
    OSS,
    S3,
    GCS,
    ABS,
    OBS,
    COS;

    public static Optional<StorageType> getStorageType(String name) {
        try {
            return Optional.of(StorageType.valueOf(name));
        } catch (IllegalArgumentException | NullPointerException ex) {
            return Optional.empty();
        }
    }
}
