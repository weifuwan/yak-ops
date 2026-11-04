package io.yak.ops.common.factory;

import io.yak.ops.common.model.OssConnection;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;

import lombok.experimental.UtilityClass;

@UtilityClass
public class OssClientFactory {

    public OSS buildOssClient(OssConnection ossConnection) {
        return new OSSClientBuilder().build(ossConnection.getEndPoint(),
                ossConnection.getAccessKeyId(), ossConnection.getAccessKeySecret());
    }
}
