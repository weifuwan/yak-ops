package io.yak.ops.authentication.aws;

import java.util.Map;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.databasemigrationservice.AWSDatabaseMigrationService;
import com.amazonaws.services.databasemigrationservice.AWSDatabaseMigrationServiceClientBuilder;

import lombok.experimental.UtilityClass;

@UtilityClass
public class AWSDatabaseMigrationServiceClientFactory {

    public AWSDatabaseMigrationService createAWSDatabaseMigrationServiceClient(Map<String, String> awsProperties) {
        AWSCredentialsProvider awsCredentialsProvider = AWSCredentialsProviderFactor.credentialsProvider(awsProperties);
        Regions regions = Regions.fromName(awsProperties.get(AwsConfigurationKeys.AWS_REGION));
        String endpoint = awsProperties.get(AwsConfigurationKeys.AWS_ENDPOINT);

        if (endpoint != null && !endpoint.isEmpty()) {
            return AWSDatabaseMigrationServiceClientBuilder
                    .standard()
                    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint, regions.getName()))
                    .withCredentials(awsCredentialsProvider)
                    .build();
        } else {
            return AWSDatabaseMigrationServiceClientBuilder
                    .standard()
                    .withCredentials(awsCredentialsProvider)
                    .withRegion(regions)
                    .build();
        }
    }

}
