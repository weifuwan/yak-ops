package io.yak.ops.common.log.remote;

import io.yak.ops.common.constants.Constants;
import io.yak.ops.common.factory.OssClientFactory;
import io.yak.ops.common.model.OssConnection;
import io.yak.ops.common.utils.PropertyUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.PutObjectRequest;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OssRemoteLogHandler implements RemoteLogHandler, Closeable {

    private OSS ossClient;

    private String bucketName;

    private static OssRemoteLogHandler instance;

    private OssRemoteLogHandler() {
        String accessKeyId = readOssAccessKeyId();
        String accessKeySecret = readOssAccessKeySecret();
        String endpoint = readOssEndpoint();
        ossClient = OssClientFactory.buildOssClient(new OssConnection(accessKeyId, accessKeySecret, endpoint));

        bucketName = readOssBucketName();
        checkBucketNameExists(bucketName);
    }

    public static synchronized OssRemoteLogHandler getInstance() {
        if (instance == null) {
            instance = new OssRemoteLogHandler();
        }

        return instance;
    }

    @Override
    public void sendRemoteLog(String logPath) {
        String objectName = RemoteLogUtils.getObjectNameFromLogPath(logPath);

        try {
            log.info("send remote log {} to OSS {}", logPath, objectName);
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, new File(logPath));
            ossClient.putObject(putObjectRequest);
        } catch (Exception e) {
            log.error("error while sending remote log {} to OSS {}", logPath, objectName, e);
        }
    }

    @Override
    public void getRemoteLog(String logPath) {
        String objectName = RemoteLogUtils.getObjectNameFromLogPath(logPath);

        try {
            log.info("get remote log on OSS {} to {}", objectName, logPath);
            ossClient.getObject(new GetObjectRequest(bucketName, objectName), new File(logPath));
        } catch (Exception e) {
            log.error("error while getting remote log on OSS {} to {}", objectName, logPath, e);
        }
    }

    @Override
    public void close() throws IOException {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    private void checkBucketNameExists(String bucketName) {
        if (StringUtils.isBlank(bucketName)) {
            throw new IllegalArgumentException(Constants.REMOTE_LOGGING_OSS_BUCKET_NAME + " is empty");
        }

        boolean existsBucket = ossClient.doesBucketExist(bucketName);
        if (!existsBucket) {
            throw new IllegalArgumentException(
                    "bucketName: " + bucketName + " is not exists, you need to create them by yourself");
        }

        log.info("bucketName: {} has been found", bucketName);
    }

    private String readOssAccessKeyId() {
        return PropertyUtils.getString(Constants.REMOTE_LOGGING_OSS_ACCESS_KEY_ID);
    }

    private String readOssAccessKeySecret() {
        return PropertyUtils.getString(Constants.REMOTE_LOGGING_OSS_ACCESS_KEY_SECRET);
    }

    private String readOssEndpoint() {
        return PropertyUtils.getString(Constants.REMOTE_LOGGING_OSS_ENDPOINT);
    }

    private String readOssBucketName() {
        return PropertyUtils.getString(Constants.REMOTE_LOGGING_OSS_BUCKET_NAME);
    }
}
