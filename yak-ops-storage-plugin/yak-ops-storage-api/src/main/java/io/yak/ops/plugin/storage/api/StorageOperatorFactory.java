package io.yak.ops.plugin.storage.api;

public interface StorageOperatorFactory {

    StorageOperator createStorageOperate();

    StorageType getStorageOperate();
}
