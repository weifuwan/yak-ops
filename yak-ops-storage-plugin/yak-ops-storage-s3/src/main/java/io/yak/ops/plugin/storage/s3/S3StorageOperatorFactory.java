package io.yak.ops.plugin.storage.s3;

import io.yak.ops.common.utils.PropertyUtils;
import io.yak.ops.plugin.storage.api.StorageOperator;
import io.yak.ops.plugin.storage.api.StorageOperatorFactory;
import io.yak.ops.plugin.storage.api.StorageType;
import io.yak.ops.plugin.storage.api.constants.StorageConstants;

import com.google.auto.service.AutoService;

@AutoService(StorageOperatorFactory.class)
public class S3StorageOperatorFactory implements StorageOperatorFactory {

    @Override
    public StorageOperator createStorageOperate() {
        final S3StorageProperties s3StorageProperties = getS3StorageProperties();
        return new S3StorageOperator(s3StorageProperties);
    }

    private S3StorageProperties getS3StorageProperties() {
        return S3StorageProperties.builder()
                .bucketName(PropertyUtils.getString(StorageConstants.AWS_S3_BUCKET_NAME))
                .s3Configuration(PropertyUtils.getByPrefix("aws.s3.", ""))
                .resourceUploadPath(PropertyUtils.getString(StorageConstants.RESOURCE_UPLOAD_PATH, "/yak-ops"))
                .build();
    }

    @Override
    public StorageType getStorageOperate() {
        return StorageType.S3;
    }
}
