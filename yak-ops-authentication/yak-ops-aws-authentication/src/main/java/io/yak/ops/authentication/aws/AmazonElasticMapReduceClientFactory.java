package io.yak.ops.authentication.aws;

import java.util.Map;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduce;
import com.amazonaws.services.elasticmapreduce.AmazonElasticMapReduceClientBuilder;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AmazonElasticMapReduceClientFactory {

    public AmazonElasticMapReduce createAmazonElasticMapReduceClient(Map<String, String> awsProperties) {
        AWSCredentialsProvider awsCredentialsProvider = AWSCredentialsProviderFactor.credentialsProvider(awsProperties);
        Regions regions = Regions.fromName(awsProperties.get(AwsConfigurationKeys.AWS_REGION));
        String endpoint = awsProperties.get(AwsConfigurationKeys.AWS_ENDPOINT);

        if (endpoint != null && !endpoint.isEmpty()) {
            return AmazonElasticMapReduceClientBuilder
                    .standard()
                    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, regions.getName()))
                    .withCredentials(awsCredentialsProvider)
                    .build();
        } else {
            return AmazonElasticMapReduceClientBuilder
                    .standard()
                    .withCredentials(awsCredentialsProvider)
                    .withRegion(regions)
                    .build();
        }
    }

}
