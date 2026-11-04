package io.yak.ops.plugin.storage.api.local;

import io.yak.ops.common.utils.PropertyUtils;
import io.yak.ops.plugin.storage.api.StorageOperator;
import io.yak.ops.plugin.storage.api.StorageOperatorFactory;
import io.yak.ops.plugin.storage.api.StorageType;
import io.yak.ops.plugin.storage.api.constants.StorageConstants;

import com.google.auto.service.AutoService;

import lombok.SneakyThrows;

@AutoService(StorageOperatorFactory.class)
public class LocalStorageOperatorFactory implements StorageOperatorFactory {

    @SneakyThrows
    @Override
    public StorageOperator createStorageOperate() {
        return new LocalStorageOperator(
                PropertyUtils.getString(StorageConstants.RESOURCE_UPLOAD_PATH, "/tmp/yak-ops"));
    }

    @Override
    public StorageType getStorageOperate() {
        return StorageType.LOCAL;
    }
}
