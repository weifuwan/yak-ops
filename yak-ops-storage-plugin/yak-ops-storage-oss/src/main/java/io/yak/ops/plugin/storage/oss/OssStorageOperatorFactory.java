package io.yak.ops.plugin.storage.oss;

import io.yak.ops.common.utils.PropertyUtils;
import io.yak.ops.plugin.storage.api.StorageOperator;
import io.yak.ops.plugin.storage.api.StorageOperatorFactory;
import io.yak.ops.plugin.storage.api.StorageType;
import io.yak.ops.plugin.storage.api.constants.StorageConstants;

import com.google.auto.service.AutoService;

@AutoService(StorageOperatorFactory.class)
public class OssStorageOperatorFactory implements StorageOperatorFactory {

    @Override
    public StorageOperator createStorageOperate() {
        return new OssStorageOperator(
                PropertyUtils.getString(StorageConstants.RESOURCE_UPLOAD_PATH, "/dolphinscheduler"));
    }

    @Override
    public StorageType getStorageOperate() {
        return StorageType.OSS;
    }
}
