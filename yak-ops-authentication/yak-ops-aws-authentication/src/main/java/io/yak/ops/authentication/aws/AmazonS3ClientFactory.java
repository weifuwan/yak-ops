package io.yak.ops.authentication.aws;

import java.util.Map;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import lombok.experimental.UtilityClass;
import software.amazon.awssdk.utils.StringUtils;

@UtilityClass
public class AmazonS3ClientFactory {

    public AmazonS3 createAmazonS3Client(Map<String, String> awsProperties) {
        AWSCredentialsProvider awsCredentialsProvider = AWSCredentialsProviderFactor.credentialsProvider(awsProperties);
        String region = awsProperties.get(AwsConfigurationKeys.AWS_REGION);
        String endpoint = awsProperties.get(AwsConfigurationKeys.AWS_ENDPOINT);

        if (endpoint != null && !endpoint.isEmpty()) {
            if (StringUtils.isEmpty(region)) {
                throw new IllegalArgumentException("The aws.s3.region cannot be empty");
            }
            return AmazonS3ClientBuilder
                    .standard()
                    .withPathStyleAccessEnabled(true)
                    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, region))
                    .withCredentials(awsCredentialsProvider)
                    .build();
        } else {
            Regions regions = Regions.fromName(region);
            return AmazonS3ClientBuilder
                    .standard()
                    .withCredentials(awsCredentialsProvider)
                    .withRegion(regions)
                    .build();
        }
    }

}
