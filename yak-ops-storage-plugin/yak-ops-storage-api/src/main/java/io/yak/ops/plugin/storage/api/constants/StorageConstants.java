package io.yak.ops.plugin.storage.api.constants;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class StorageConstants {

    /**
     * resource.hdfs.fs.defaultFS
     */
    public static final String FS_DEFAULT_FS = "resource.hdfs.fs.defaultFS";

    /**
     * hdfs defaultFS property name. Should be consistent with the property name in hdfs-site.xml
     */
    public static final String HDFS_DEFAULT_FS = "fs.defaultFS";

    /**
     * hdfs configuration
     * resource.hdfs.root.user
     */
    public static final String HDFS_ROOT_USER = "resource.hdfs.root.user";

    /**
     * hdfs/s3 configuration
     * resource.storage.upload.base.path
     */
    public static final String RESOURCE_UPLOAD_PATH = "resource.storage.upload.base.path";

    public static final String RESOURCE_STORAGE_TYPE = "resource.storage.type";

    public static final String AWS_S3_BUCKET_NAME = "aws.s3.bucket.name";

    public static final String ALIBABA_CLOUD_OSS_BUCKET_NAME = "resource.alibaba.cloud.oss.bucket.name";
    public static final String ALIBABA_CLOUD_OSS_END_POINT = "resource.alibaba.cloud.oss.endpoint";

}
