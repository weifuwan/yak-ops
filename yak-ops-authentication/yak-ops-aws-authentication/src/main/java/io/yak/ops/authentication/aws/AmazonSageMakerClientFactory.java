package io.yak.ops.authentication.aws;

import java.util.Map;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.sagemaker.AmazonSageMaker;
import com.amazonaws.services.sagemaker.AmazonSageMakerClientBuilder;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AmazonSageMakerClientFactory {

    public AmazonSageMaker createAmazonSageMakerClient(Map<String, String> awsProperties) {
        AWSCredentialsProvider awsCredentialsProvider = AWSCredentialsProviderFactor.credentialsProvider(awsProperties);
        Regions regions = Regions.fromName(awsProperties.get(AwsConfigurationKeys.AWS_REGION));
        String endpoint = awsProperties.get(AwsConfigurationKeys.AWS_ENDPOINT);

        if (endpoint != null && !endpoint.isEmpty()) {
            return AmazonSageMakerClientBuilder
                    .standard()
                    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, regions.getName()))
                    .withCredentials(awsCredentialsProvider)
                    .build();
        } else {
            return AmazonSageMakerClientBuilder
                    .standard()
                    .withCredentials(awsCredentialsProvider)
                    .withRegion(regions)
                    .build();
        }
    }

}
