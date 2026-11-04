package io.yak.ops.authentication.aws;

import java.util.Map;

import lombok.experimental.UtilityClass;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.datasync.DataSyncClient;

@UtilityClass
public class DataSyncClientFactory {

    public DataSyncClient createDataSyncClient(Map<String, String> awsProperties) {
        // todo: upgrade the version of aws sdk
        String awsAccessKeyId = awsProperties.get(AwsConfigurationKeys.AWS_ACCESS_KEY_ID);
        String awsSecretAccessKey = awsProperties.get(AwsConfigurationKeys.AWS_SECRET);
        final AwsBasicCredentials basicAWSCredentials = AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey);
        final AwsCredentialsProvider awsCredentialsProvider = StaticCredentialsProvider.create(basicAWSCredentials);

        // create a datasync client
        return DataSyncClient.builder()
                .region(Region.of(awsProperties.get(AwsConfigurationKeys.AWS_REGION)))
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }

}
