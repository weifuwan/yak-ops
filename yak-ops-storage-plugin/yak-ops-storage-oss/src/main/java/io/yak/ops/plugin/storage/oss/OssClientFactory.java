package io.yak.ops.plugin.storage.oss;

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
